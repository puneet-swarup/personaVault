-- V6: Generic entity + fact model (ADR-017).
-- Replaces the single-purpose 'policies' table with a domain-agnostic model.
-- The old 'policies' table is retained during migration and exposed via a view
-- so existing code (RenewalCheckerService, PolicyRepository) keeps working.

-- ---------------------------------------------------------------------------
-- entity: a thing the vault knows about (person, policy, insurer, account, ...)
-- ---------------------------------------------------------------------------
CREATE TABLE entity (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_type     VARCHAR(40)  NOT NULL,
    canonical_name  VARCHAR(255) NOT NULL,
    aliases         TEXT[]       NOT NULL DEFAULT '{}',
    schema_id       VARCHAR(60),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMP WITH TIME ZONE
);

-- Temporary link back to the legacy policies row, used only during the
-- backfill below so the join is unambiguous even if two policies share a
-- policy_number. Dropped in a later migration once consumers use the vault.
ALTER TABLE entity ADD COLUMN legacy_policy_id BIGINT;
CREATE UNIQUE INDEX idx_entity_legacy_policy ON entity (legacy_policy_id);

CREATE INDEX idx_entity_type_name ON entity (entity_type, canonical_name);
CREATE INDEX idx_entity_aliases   ON entity USING gin (aliases);

-- ---------------------------------------------------------------------------
-- fact: a typed, provenance-tracked value about an entity (ADR-017, 019, 021)
-- ---------------------------------------------------------------------------
CREATE TABLE fact (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    entity_id           BIGINT      NOT NULL REFERENCES entity(id),
    fact_key            VARCHAR(80) NOT NULL,
    value_type          VARCHAR(20) NOT NULL,
    value_text          TEXT,
    value_num           NUMERIC(18,4),
    value_date          DATE,
    value_entity_id     BIGINT REFERENCES entity(id),
    currency            VARCHAR(3),
    valid_from          DATE,
    valid_to            DATE,
    confidence          NUMERIC(3,2) NOT NULL DEFAULT 0.75,
    verification_status VARCHAR(20) NOT NULL DEFAULT 'EXTRACTED',
    extracted_by        VARCHAR(20) NOT NULL,
    source_doc_id       BIGINT      NOT NULL REFERENCES documents(id),
    source_page         INTEGER,
    source_snippet      TEXT,
    superseded_by       BIGINT REFERENCES fact(id),
    first_seen_at       TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_confirmed_at   TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_fact_confidence CHECK (confidence >= 0.00 AND confidence <= 1.00)
);

CREATE INDEX idx_fact_entity_key   ON fact (entity_id, fact_key);
CREATE INDEX idx_fact_key_date     ON fact (fact_key, value_date);
CREATE INDEX idx_fact_source_doc   ON fact (source_doc_id);
CREATE INDEX idx_fact_validity     ON fact (entity_id, fact_key, valid_from, valid_to);

-- ---------------------------------------------------------------------------
-- Backfill: migrate existing 'policies' rows into entity + fact.
-- Each policy becomes one entity (type=POLICY) plus one fact per column.
-- ---------------------------------------------------------------------------

-- 1. One entity per policy row. legacy_policy_id is a temporary bridge so the
--    fact backfill below joins unambiguously even when two policies share a
--    policy_number. It is dropped in a later migration.
INSERT INTO entity (entity_type, canonical_name, schema_id, created_at, legacy_policy_id)
SELECT 'POLICY', p.policy_number, 'insurance-policy', p.extracted_at, p.id
FROM policies p;

-- 2. Fact rows, joined back to their entity through the temporary bridge.
INSERT INTO fact (entity_id, fact_key, value_type, value_text, value_date,
                  value_num, confidence, verification_status, extracted_by,
                  source_doc_id, first_seen_at, last_confirmed_at)
SELECT e.id, 'policyNumber', 'STRING', p.policy_number, NULL,
       NULL, 0.95, CASE WHEN p.verified THEN 'CONFIRMED' ELSE 'EXTRACTED' END,
       'LLM', p.document_id, p.extracted_at,
       CASE WHEN p.verified THEN p.extracted_at ELSE NULL END
FROM policies p JOIN entity e ON e.legacy_policy_id = p.id;

INSERT INTO fact (entity_id, fact_key, value_type, value_text, confidence,
                  verification_status, extracted_by, source_doc_id, first_seen_at)
SELECT e.id, 'insurerName', 'STRING', p.insurer_name, 0.75, 'EXTRACTED', 'LLM', p.document_id, p.extracted_at
FROM policies p JOIN entity e ON e.legacy_policy_id = p.id
WHERE p.insurer_name IS NOT NULL;

INSERT INTO fact (entity_id, fact_key, value_type, value_text, confidence,
                  verification_status, extracted_by, source_doc_id, first_seen_at)
SELECT e.id, 'policyType', 'STRING', p.policy_type, 0.75, 'EXTRACTED', 'LLM', p.document_id, p.extracted_at
FROM policies p JOIN entity e ON e.legacy_policy_id = p.id;

INSERT INTO fact (entity_id, fact_key, value_type, value_date, confidence,
                  verification_status, extracted_by, source_doc_id, first_seen_at)
SELECT e.id, 'effectiveDate', 'DATE', p.effective_date, 0.75, 'EXTRACTED', 'LLM', p.document_id, p.extracted_at
FROM policies p JOIN entity e ON e.legacy_policy_id = p.id
WHERE p.effective_date IS NOT NULL;

INSERT INTO fact (entity_id, fact_key, value_type, value_date, confidence,
                  verification_status, extracted_by, source_doc_id, first_seen_at)
SELECT e.id, 'expiryDate', 'DATE', p.expiry_date, 0.95, 'EXTRACTED', 'LLM', p.document_id, p.extracted_at
FROM policies p JOIN entity e ON e.legacy_policy_id = p.id;

INSERT INTO fact (entity_id, fact_key, value_type, value_num, currency, confidence,
                  verification_status, extracted_by, source_doc_id, first_seen_at)
SELECT e.id, 'annualPremium', 'MONEY', p.annual_premium, 'INR', 0.75, 'EXTRACTED', 'LLM', p.document_id, p.extracted_at
FROM policies p JOIN entity e ON e.legacy_policy_id = p.id
WHERE p.annual_premium IS NOT NULL;

INSERT INTO fact (entity_id, fact_key, value_type, value_text, confidence,
                  verification_status, extracted_by, source_doc_id, first_seen_at)
SELECT e.id, 'premiumFrequency', 'STRING', p.premium_frequency, 0.75, 'EXTRACTED', 'LLM', p.document_id, p.extracted_at
FROM policies p JOIN entity e ON e.legacy_policy_id = p.id
WHERE p.premium_frequency IS NOT NULL;

-- ---------------------------------------------------------------------------
-- policy_view: exposes the old 'policies' shape from entity + fact, so existing
-- consumers (RenewalCheckerService, PolicyRepository native queries) can migrate
-- gradually. Read-only; writes still go to the old table until Phase 3.
-- ---------------------------------------------------------------------------
CREATE VIEW policy_view AS
SELECT
    e.id AS id,
    MAX(CASE WHEN f.fact_key = 'policyNumber'     THEN f.value_text END) AS policy_number,
    MAX(CASE WHEN f.fact_key = 'insurerName'      THEN f.value_text END) AS insurer_name,
    MAX(CASE WHEN f.fact_key = 'policyType'       THEN f.value_text END) AS policy_type,
    MAX(CASE WHEN f.fact_key = 'effectiveDate'    THEN f.value_date END) AS effective_date,
    MAX(CASE WHEN f.fact_key = 'expiryDate'       THEN f.value_date END) AS expiry_date,
    MAX(CASE WHEN f.fact_key = 'annualPremium'    THEN f.value_num  END) AS annual_premium,
    MAX(CASE WHEN f.fact_key = 'premiumFrequency' THEN f.value_text END) AS premium_frequency,
    MIN(f.source_doc_id) AS document_id,
    MIN(f.first_seen_at) AS extracted_at,
    BOOL_OR(f.verification_status = 'CONFIRMED') AS verified
FROM entity e
JOIN fact f ON f.entity_id = e.id
WHERE e.entity_type = 'POLICY' AND e.deleted_at IS NULL
GROUP BY e.id;

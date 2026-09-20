-- V2: Structured policy records extracted from documents.
-- This is the source of truth for exact facts (dates, amounts, IDs) per ADR-007.
-- The LLM never answers date/amount questions from memory — it queries this table.

CREATE TABLE policies (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    policy_number       VARCHAR(100) NOT NULL,
    insurer_name        VARCHAR(256),
    policy_type         VARCHAR(50) NOT NULL,
    effective_date      DATE,
    expiry_date         DATE NOT NULL,
    annual_premium      DECIMAL(12,2),
    premium_frequency   VARCHAR(20),
    document_id         BIGINT NOT NULL REFERENCES documents(id),
    extracted_at        TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    verified            BOOLEAN NOT NULL DEFAULT FALSE
);

-- Renewal checker queries this index daily
CREATE INDEX idx_policies_expiry ON policies (expiry_date);

-- Dashboard filters by type
CREATE INDEX idx_policies_type ON policies (policy_type);

-- Cascade: when a document is hard-deleted, its policies go too
-- (We handle this in application code, not DB constraint, to allow soft-delete)
CREATE INDEX idx_policies_document ON policies (document_id);
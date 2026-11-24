# ADR-017: Generic Typed Entity + Fact Model (Policy as a View)

## Status
Proposed (2026-10-29)

## Context
The current fact layer is a single hard-coded `Policy` entity with fixed columns (`policyNumber`, `insurerName`, `policyType`, `effectiveDate`, `expiryDate`, `annualPremium`, `premiumFrequency`). This was the right MVP for insurance but does not generalise: medical reports need providers, conditions, medications, dosages; bank statements need accounts, transactions, balances; ID documents need issuers, numbers, expiry.

Adding a table per document type does not scale, cannot express "same fact changed over time", and makes cross-domain queries ("everything expiring in the next 60 days") impossible without UNIONs.

The motto demands a single, uniform truth model that any domain can populate.

## Decision
Replace `Policy` with a generic two-table model:

### `entity`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `entity_type` | VARCHAR(40) | PERSON, POLICY, INSURER, ACCOUNT, PROVIDER, MEDICATION, CONDITION, PROPERTY, VEHICLE, EVENT, ... |
| `canonical_name` | VARCHAR(255) | normalised display name |
| `aliases` | TEXT[] | alternative names for matching |
| `schema_id` | VARCHAR(60) | which extraction schema produced it (ADR-018) |
| `created_at`, `updated_at` | TIMESTAMPTZ | |
| `deleted_at` | TIMESTAMPTZ | soft delete, consistent with ADR-013 |

### `fact`
| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `entity_id` | BIGINT FK | |
| `fact_key` | VARCHAR(80) | "expiryDate", "annualPremium", "dosage" |
| `value_type` | VARCHAR(20) | STRING, NUMBER, MONEY, DATE, BOOLEAN, ENTITY_REF |
| `value_text` | TEXT | canonical string form |
| `value_num` | NUMERIC | when numeric |
| `value_date` | DATE | when date |
| `value_entity_id` | BIGINT FK NULL | when ENTITY_REF |
| `currency` | VARCHAR(3) NULL | when MONEY |
| `valid_from`, `valid_to` | DATE NULL | temporal validity (ADR-019) |
| `confidence` | NUMERIC(3,2) | 0.00-1.00 (ADR-021) |
| `verification_status` | VARCHAR(20) | EXTRACTED, USER_CORRECTED, CONFIRMED, STALE |
| `extracted_by` | VARCHAR(20) | REGEX, LLM, USER |
| `source_doc_id` | BIGINT FK | provenance |
| `source_page` | INT NULL | provenance |
| `source_snippet` | TEXT NULL | provenance |
| `superseded_by` | BIGINT FK NULL | when a newer fact replaces this one |
| `first_seen_at`, `last_confirmed_at` | TIMESTAMPTZ | |

### `Policy` becomes a read view
A SQL view `policy_view` joins `entity WHERE entity_type='POLICY'` with the relevant fact keys, exposing exactly the columns the old `policies` table had. `RenewalCheckerService`, `PolicyRepository` callers, and the existing UI keep working unchanged. A migration copies current `policies` rows into `entity` + `fact`.

### Indexing
- `entity(entity_type, canonical_name)`
- `entity USING gin(aliases)`
- `fact(entity_id, fact_key)`
- `fact(fact_key, value_date)` — for "all expiries in 60 days"
- `fact(source_doc_id)` — for deletion cascade

## Consequences
- Plus: One model for all domains; new domain = new schema, not new table (ADR-018)
- Plus: Temporal validity and versioning are expressible (ADR-019)
- Plus: Cross-domain queries become single SQL ("all expiring items")
- Plus: `Policy` consumers keep working through the view — no big-bang rewrite
- Minus: Generic facts are less type-safe than dedicated columns; mitigated by `value_type` + validation in the schema layer
- Minus: Queries are slightly more complex; mitigated by the `policy_view` and future typed views
- Minus: Migration risk when copying `policies`; mitigated by keeping the old table until Phase 2.5 exit criteria pass

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Keep `Policy` + add a table per domain | N tables, no cross-domain query, no temporal model |
| EAV with no types (`key`,`value` strings only) | Loses numeric/date indexing; can't do "expiring in 60 days" efficiently |
| JSONB blob per entity | Hard to query and validate; provenance per fact impossible |
| Typed sub-tables per entity (`policy`, `account`, ...) | Strong constraints but the biggest fork; rejected per 2026-10-29 decision in favour of generic + typed schemas |

## Decision record
2026-10-29: Chosen generic `entity` + `fact` with typed schemas in metadata and `Policy` as a view (option 1 in the design discussion). Rationale: flexibility to extend to other data sources/types.

## Related
ADR-015, ADR-016, ADR-018 (schemas), ADR-019 (temporal), ADR-021 (confidence), ADR-026 (cascade)

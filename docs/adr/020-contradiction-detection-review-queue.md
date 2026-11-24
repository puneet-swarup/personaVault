# ADR-020: Contradiction Detection & Review Queue

## Status
Proposed (2026-10-29)

## Context
With temporal facts (ADR-019), a contradiction has a precise meaning: two facts for the same entity + key whose validity intervals overlap and whose values differ. These must never be silently overwritten — the user must see and resolve them.

Without a review queue, the vault would either lose information or drift toward whichever extraction ran last.

## Decision

### `claim_conflict` table
| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `entity_id` | BIGINT FK | |
| `fact_key` | VARCHAR(80) | |
| `fact_a_id`, `fact_b_id` | BIGINT FK | the two disagreeing facts |
| `status` | VARCHAR(20) | OPEN, RESOLVED_A, RESOLVED_B, RESOLVED_CUSTOM, DISMISSED |
| `resolution_fact_id` | BIGINT NULL | chosen winner |
| `resolution_note` | TEXT NULL | |
| `resolved_at` | TIMESTAMPTZ NULL | |
| `created_at` | TIMESTAMPTZ | |

### Detection
Runs as part of the extraction commit, in the same transaction. For each new fact, look up existing facts for the same entity + key with overlapping validity and different values. If found, insert a conflict row and hold the new fact in `verification_status = EXTRACTED` rather than promoting it.

### Resolution policy (decided 2026-10-29)
- Tier 2 facts (exact values): human confirmation required. The user sees a review item with both facts, both source snippets, and picks or edits.
- Tier 3 wiki narrative: auto-resolve by rendering both and letting the next nightly recompile settle the wording. No blocking.

### Surfaces
1. Review queue UI — list of open conflicts, side-by-side sources, one-click resolve.
2. Telemetry dashboard (ADR-025) — open conflict count.
3. Answer-time hedge — if an answer depends on a fact under conflict, the citation carries `confidence` downgraded and a flag the UI renders ("disputed value").

### Non-conflicts (explicitly)
- No temporal overlap — later fact simply supersedes; no conflict row.
- Same value, different source — merge provenance, add source, raise confidence. No conflict.
- Different granularity (5L vs 5,00,000) — numeric normalisation runs before comparison; not a conflict.

## Consequences
- Plus: Disagreement is surfaced, never lost
- Plus: Human confirmation keeps exact values trustworthy
- Plus: Conflict history is an audit trail of source reliability
- Minus: Review backlog if many conflicts; mitigated by auto-merge rules for non-conflicts and a batch-resolve UI
- Minus: Adds a write-time lookup; mitigated by an index on `(entity_id, fact_key, valid_from, valid_to)`

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Newer-source-wins silently | Hides disagreement; violates trust motto |
| Reject conflicting fact | Loses information; source may be more authoritative |
| Auto-resolve Tier 2 by confidence | Confidence is heuristic; not safe for exact values |
| Ask LLM to arbitrate | Non-deterministic; the user is the authority |

## Related
ADR-016 (Tier 2 authority), ADR-019 (temporal), ADR-021 (confidence), ADR-024 (corrections), ADR-025 (telemetry)

# ADR-019: Temporal Fact Validity & Versioning

## Status
Proposed (2026-10-29)

## Context
A health policy's premium going from 5L to 10L is not a contradiction — it is a change over time. The current `policies` table cannot express this: a new extraction either overwrites the old row or creates a duplicate, and the UI/alerts have no way to know which value applied when.

For the vault to honestly say "premium rose from 5L to 10L in 2025", facts need temporal validity.

## Decision
Every `fact` row carries `valid_from` and `valid_to` (nullable). Null means open-ended.

### Rules
1. New fact on same entity + key with overlapping validity is a candidate contradiction (ADR-020), not an overwrite.
2. New fact on same entity + key with a later `valid_from` and no overlap closes the prior fact (`valid_to = new.valid_from - 1 day`) and inserts the new one. This is normal evolution, not a conflict.
3. `superseded_by` links old to new for audit.
4. Point-in-time queries use `WHERE valid_from <= :asOf AND (valid_to IS NULL OR valid_to >= :asOf)`.
5. Current-value queries use `valid_to IS NULL`.

The wiki compiler uses this to render timelines ("2024: 5L -> 2025: 10L -> 2026: 12L") and to answer "what was my coverage in March 2025?".

### Backfill
Existing `policies` rows migrate with `valid_from = effectiveDate`, `valid_to = NULL`, `superseded_by = NULL`. No historical versions exist yet; that is acceptable for Phase 2.5.

## Consequences
- Plus: Change is modelled correctly instead of as error
- Plus: Enables trend questions and timelines in the wiki
- Plus: Audit trail of how facts evolved
- Plus: Point-in-time reconstruction for compliance-style questions
- Minus: Slightly more complex queries; mitigated by helper repository methods
- Minus: Requires care during extraction to infer `valid_from` when the doc does not state one (fallback: ingestion date, flagged low confidence)

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Last-write-wins | Destroys history; cannot narrate change |
| Append-only event log only, no intervals | No efficient point-in-time query |
| Bi-temporal (valid time + transaction time) | Overkill for single-user; defer |

## Related
ADR-017 (fact schema), ADR-020 (contradiction), ADR-022 (timelines in wiki)

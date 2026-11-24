# ADR-021: Confidence Scoring & Provenance Requirements

## Status
Proposed (2026-10-29)

## Context
The vault's promise — "correct data, no haystack search" — only holds if every surfaced fact is traceable and its reliability is visible. Without confidence and provenance, the system is just a faster way to be wrong.

Decision from design discussion (2026-10-29): answers should show an explicit confidence percentage and a source link, so the user is always aware of reliability.

## Decision

### Provenance is mandatory
The following are required on every `fact`:
- `source_doc_id` (must reference a live or soft-deleted-but-retained document)
- at least one of `source_snippet` or `source_page`
- `extracted_by` in {REGEX, LLM, USER}

A fact that cannot cite a source is rejected at write time. This is enforced in the `FactRepository.saveFact()` path and covered by a test.

Wiki pages must footnote every claim to a fact or source (ADR-022).

### Confidence model
Base confidence by extractor:

| `extracted_by` | Base |
|---|---|
| REGEX with validation PASS | 0.95 |
| REGEX without validation | 0.85 |
| LLM (schema-guided) | 0.75 |
| USER_CORRECTED | 1.00 |

Adjustments (schema-driven, ADR-018 `confidence_rules`):
- Required field found + validation passed: +0.05 (cap at 0.99)
- Value re-extracted identically on a second pass: +0.03
- Numeric normalisation performed: -0.02
- Inferred `valid_from` (not stated in doc): -0.10
- Under open conflict (ADR-020): cap at 0.50

`verification_status` tracks lifecycle: EXTRACTED → (CONFIRMED | USER_CORRECTED | STALE).

### Display contract
Answers carry confidence as a percentage. UI renders e.g.:
`"Premium: 24,000 (92% - 1 source)"` — click reveals the snippet.
Below 0.60, the answer is visually hedged ("likely") and the UI surfaces the sources prominently.

### Confidence is stored, not recomputed
Confidence is computed once at write time and stored. Re-derivation on every read would be slow and non-reproducible. Recompute only on explicit correction or re-extraction.

## Consequences
- Plus: Every fact is auditable back to a document
- Plus: User sees reliability, not just an answer
- Plus: Confidence drives routing hints (low-confidence facts may fall back to source view)
- Minus: Slightly larger `fact` table; acceptable
- Minus: Confidence is heuristic; treated as a hint, not a guarantee

## Alternatives Considered
| Option | Why rejected |
|---|---|
| No confidence, all facts equal | Hides reliability; user cannot judge |
| Recompute confidence per query | Slow; non-deterministic |
| Single global confidence per document | Too coarse; per-fact differences matter |
| Confidence from LLM self-report | Unreliable; ignored |

## Related
ADR-017 (fact schema), ADR-018 (schema rules), ADR-020 (conflict cap), ADR-024 (user correction = 1.00)

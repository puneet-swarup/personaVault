# ADR-024: Corrections as First-Class + Feedback into Extraction

## Status
Proposed (2026-10-29)

## Context
The earlier roadmap deferred "manual corrections" to Phase 3 as a nice-to-have. In the truth-vault framing (ADR-015) corrections are core: they are the mechanism by which accuracy compounds. A user who fixes a wrong premium should not have to fix the same kind of mistake on the next document.

## Decision

### Corrections are first-class
a `correction` table records every user edit:

| Column | Notes |
|---|---|
| `id` | PK |
| `target_type` | FACT / WIKI_PAGE |
| `target_id` | |
| `before_value`, `after_value` | text snapshots |
| `reason` | optional note |
| `by_user` | single-user flag |
| `at` | timestamptz |

When a fact is corrected:
- The old fact is superseded (`superseded_by`), the new fact gets `extracted_by = USER`, `confidence = 1.00`, `verification_status = USER_CORRECTED`.
- Any conflict involving the old fact is auto-resolved as RESOLVED_CUSTOM (ADR-020).
- Dependent wiki pages are queued for recompile.

When a wiki page is corrected:
- The page's `version` is bumped; `needsReview = false`; the edit is preserved across nightly recompiles unless a fact contradicts it (then `needsReview` is re-raised).
- User edits win over compiler output for the sections they touch; the compiler treats user-edited sections as pinned.

### Feedback into extraction
Corrections are stored as few-shot examples per schema id. The extraction prompt builder (ADR-018) prepends the most recent corrections for the schema (bounded, e.g. 5 examples) as few-shot pairs. Over time, the extractor learns the specific layouts and habits present in the user's documents.

A correction also updates a small per-schema "known pitfalls" note used in the prompt text.

### Surfaces
- Correcting a fact inline from any answer (the citation popover includes an "edit" action).
- Correcting a wiki page in the in-app viewer (Phase 3) or in Obsidian (file change detected on next compile).
- A correction history list for audit.

## Consequences
- Plus: The same mistake is less likely to recur — accuracy genuinely compounds
- Plus: User is the ultimate authority; nothing is unfixable
- Plus: Corrections are auditable and reversible
- Plus: Few-shot feedback needs no model retraining; works with local Ollama
- Minus: A bad correction propagates through few-shot examples; mitigated by bounding to recent N and allowing deletion of a correction
- Minus: Obsidian-side edits need reconciliation; mitigated by file-mtime detection and "file wins" rule (ADR-022)

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Corrections as simple overwrite | Loses audit trail; no learning signal |
| Fine-tune the local model on corrections | Costly, slow, non-deterministic; few-shot suffices |
| No feedback loop | Accuracy stagnates; motto not delivered |
| Corrections only in Obsidian | Poor UX for the fast path; in-app edit needed |

## Related
ADR-018 (prompt builder), ADR-020 (conflict resolution), ADR-021 (confidence), ADR-022 (file-wins), ADR-025 (telemetry)

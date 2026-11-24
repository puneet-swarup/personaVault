# ADR-026: Extended Deletion Cascade & Fact Invalidation

## Status
Proposed (2026-10-29)

## Context
ADR-013 defines the deletion cascade for a document: vectors, policies, notifications, file, and soft-delete. With the vault architecture (ADR-015/017), deleting a document now also invalidates facts and wiki pages derived from it. Silent removal would leave unsupported claims in the vault — a trust violation.

## Decision
Extend `IngestionService.delete(id)` to run a cascade in this order, transactionally:

1. Delete vectors (as today).
2. Facts sourced from the document: for each fact with `source_doc_id = id`:
   - If the fact has no other source, mark `verification_status = STALE`, set `last_confirmed_at = NULL` (do not delete — history matters).
   - If the fact has other sources, remove this doc from provenance only; fact stays valid; recompute confidence if applicable (ADR-021).
   - Any `claim_conflict` involving a now-stale fact is auto-closed as DISMISSED with note "source deleted".
3. Wiki pages referencing the document: set `needs_review = true`; queue a recompile job (per ADR-022). The compiler removes or hedges claims whose facts went STALE; if a page loses all facts, it is marked `orphaned` and surfaced in telemetry (ADR-025).
4. Policies / notifications: unchanged from ADR-013.
5. Document row: soft-delete (`deleted_at`), consistent with ADR-013.
6. File on disk: delete.

### Recompile job
A `wikiExecutor` task runs after the transaction commits. It recompiles the affected pages, honours user-pinned sections (ADR-024), and updates the index.

### Telemetry
STALE facts and `needs_review` pages appear on the dashboard (ADR-025). The user can act on them — re-upload a newer version, correct manually, or dismiss.

## Consequences
- Plus: The vault never silently keeps claims whose evidence is gone
- Plus: Staleness is visible and actionable
- Plus: History is preserved (facts are not deleted, just marked)
- Minus: Deletion now touches more tables; slower but bounded (index on `source_doc_id`)
- Minus: Recompile adds background work after a delete; acceptable
- Minus: Some pages become partly hollow until the user re-uploads; surfaced via telemetry

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Delete facts outright | Loses history; can break pages silently |
| Leave facts untouched | Violates trust motto; unsupported claims remain |
| Hard-delete pages referencing the doc | Too destructive; user loses narrative |
| Refuse deletion if facts exist | Hostile UX; user must be able to delete |

## Related
ADR-013 (original cascade), ADR-019 (temporal), ADR-020 (conflicts), ADR-022 (wiki), ADR-025 (telemetry)

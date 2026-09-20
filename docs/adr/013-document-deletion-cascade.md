# ADR-013: Document Deletion Cascade (Vectors + Policies + Notifications + File)

## Status
Accepted (2026-09-20)

## Context
When a user deletes a document, all derived data must be cleaned up:
- **Vector embeddings**: if retained, RAG retrieves stale chunks and the LLM answers based on deleted content, citing a document that no longer exists in the list
- **Extracted policy records**: derived data with no provenance if the source is gone
- **Notifications**: reference a policy that no longer exists (dead references)
- **Physical file**: stale data on disk; user might accidentally re-ingest the old version

## Decision
Cascade delete on document removal, in this order:
1. Delete vectors from PGVector (by `documentRef` metadata query)
2. Hard-delete associated `policies` rows
3. Hard-delete associated `notifications` rows
4. Delete physical file from `./data/documents/`
5. Soft-delete the `documents` row (set `deletedAt`, retain for audit)

The soft-delete of the documents row is the **atomic commit point**. If any earlier step fails, the document remains intact and the user can retry.

## Consequences
- ✅ No stale RAG results after deletion
- ✅ No orphaned policy/notification rows
- ✅ No stale files on disk
- ✅ Audit trail retained (documents row with `deletedAt` timestamp)
- ✅ User can re-upload the same filename (UUID prefix prevents collision)
- ❌ Irreversible (no "undo delete") — acceptable; user confirms via dialog
- ❌ If vector deletion fails mid-cascade, document is in inconsistent state (mitigated: DB ops in transaction; file deletion is best-effort with logging)

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Soft-delete only (keep vectors, filter in RAG query) | Complex: requires filtering in every RAG call; vectors still consume memory; easy to forget the filter |
| Hard-delete documents row too | Lose audit trail; can't answer "what did the user delete last month?" |
| Keep policies, mark as "source deleted" | Stale data with a flag is worse than no data; user might trust it |
| No file deletion (keep on disk) | Stale data; disk clutter; user might re-ingest old file by accident |
| `ON DELETE CASCADE` DB constraint | Can't cascade to the vector store (different table schema, JSONB metadata); application-level cascade gives more control and logging |   
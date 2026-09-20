# ADR-011: Async Extraction (CompletableFuture) Over Synchronous

## Status
Accepted (2026-09-20)

## Context
Structured extraction requires an LLM call (5-30s on local hardware). If extraction runs synchronously during upload, the user stares at a loading spinner for 30+ seconds before seeing their document in the list. The upload itself (Tika parse + vector embed) takes 3-10s — already acceptable. Adding extraction on top makes the response unacceptably slow.

## Decision
Run extraction **asynchronously** after the upload response is sent. Use Spring's `@Async` with a dedicated thread pool (`extractionExecutor`, 2 threads, 10 queue). The upload returns 201 immediately after vector storage. Extraction completes in the background.

## Consequences
- ✅ Upload response time unchanged (3-10s for parse + embed)
- ✅ User can upload multiple files without waiting for extraction
- ✅ Extraction failure doesn't block the upload or corrupt the vector store
- ❌ Policy data is not immediately available after upload (5-30s delay)
- ❌ If the app crashes during extraction, the policy is never created (mitigated: re-extract on startup in Phase 3)
- ❌ Two concurrent extractions max (thread pool size = 2) — 3rd file queues

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Synchronous extraction (block upload response) | 30s+ wait; bad UX; user thinks upload failed |
| Message queue (RabbitMQ, Kafka) | Massive overkill for single-user, 2 concurrent max |
| Spring Batch | Overkill for a single LLM call |
| CompletableFuture without @Async | Manual thread management; @Async is the Spring idiom |   
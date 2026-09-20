# ADR-002: PostgreSQL + PGVector Over Chroma/Qdrant

## Status
Accepted (2026-09-19)

## Context
The system needs two data capabilities:
1. **Vector similarity search** — for RAG (retrieve relevant document chunks)
2. **Relational queries** — for renewal dates, premium totals, policy joins, notification history

Using a separate vector database (Chroma, Qdrant, Weaviate) adds an extra service, volume, backup concern, and makes cross-querying between vectors and relational data awkward.

## Decision
Use **PostgreSQL 16 with the `pgvector` extension** as the single database for both vectors and relational data. Deploy via the `pgvector/pgvector:pg16` Docker image.

## Consequences
- ✅ Single database to manage, back up, and query
- ✅ SQL joins between `policies` and `vector_store` for hybrid queries
- ✅ Familiar tooling (psql, pgAdmin, standard Postgres backups)
- ✅ Sufficient performance for <500 documents / ~50K chunks
- ✅ Spring AI has first-class PGVector support
- ❌ Not as performant as dedicated vector DBs at 10M+ vectors (irrelevant at this scale)
- ❌ Requires the `pgvector` Docker image (minor operational difference)

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Chroma | Extra service; Python-native; no relational queries; harder to join with policy data |
| Qdrant | Extra container + volume; overkill for single-user <500 docs; no SQL |
| Weaviate | Heavier; GraphQL API; same "extra service" problem |
| In-memory HNSW (Java) | No persistence; re-embed all chunks on every restart |
| SQLite + sqlite-vec | No concurrent writes; weaker SQL; less mature; no HNSW index |   
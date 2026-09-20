# ADR-012: Configurable Alert Thresholds (DB + In-Memory Cache)

## Status
Accepted (2026-09-20)

## Context
Renewal alerts need per-policy-type thresholds (e.g., 60 days for health, 30 days for car). Hardcoding these in Java means a code change + redeploy to adjust. Environment variables work but require restart and don't support per-type granularity in a queryable way. The user may want to change a threshold at runtime ("I want car alerts at 45 days, not 30").

## Decision
Store thresholds in a **database table** (`alert_thresholds`). Load into an **in-memory `ConcurrentHashMap`** at startup. The renewal checker reads from the cache (zero DB hit per check). Updates write to DB + refresh the cache entry.

## Consequences
- ✅ Change threshold without restart or code change
- ✅ Per-policy-type granularity in one queryable table
- ✅ Zero DB hits during the daily check (cached)
- ✅ Queryable from dashboard later ("show me my alert config")
- ❌ One extra table to maintain
- ❌ Cache/DB consistency: if DB is updated externally (psql), cache is stale until restart or explicit update call
- ❌ Slightly more complex than a constants class

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Hardcoded constants in Java | Requires redeploy to change; not user-configurable |
| Environment variables | Require restart; all-or-nothing (can't do per-type in one var cleanly) |
| application.yml | Same as env vars; requires restart |
| Spring `@Cacheable` (Redis/Hazelcast) | External cache dependency for 6-15 entries; overkill |
| No caching (query DB every check) | Negligible cost at this scale, but adds a DB dependency to the scheduler for zero benefit |   
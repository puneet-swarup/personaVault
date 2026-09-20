# ADR-006: @Scheduled Over db-scheduler (v1)

## Status
Accepted (2026-09-19)

## Context
Renewal checks must run daily at 8 AM IST. The app is single-user, single-instance. If the app is down at 8 AM (machine off, reboot), the check should run on next startup as a catch-up.

## Decision
Use Spring's **`@Scheduled`** annotation for v1. Add a startup catch-up check (`@PostConstruct` or `ApplicationReadyEvent`). Migrate to **db-scheduler** in v2 if multi-instance or complex retry semantics become necessary.

## Consequences
- ✅ Zero extra dependency; one annotation
- ✅ Sufficient for single-user, single-instance
- ✅ Cron expression is readable and standard
- ✅ Startup catch-up handles the "was down at 8 AM" case
- ❌ No persistent job history (not needed for v1)
- ❌ No built-in retry/backoff (acceptable: daily check is idempotent)
- ❌ No multi-instance safety (not a concern: single user, single machine)

## Alternatives Considered
| Option | Why rejected |
|---|---|
| db-scheduler | Better (persistent, retry, multi-instance) but extra dependency + table + config; overkill for v1 |
| Quartz | Heavy; clustering support not needed; more configuration |
| System cron + REST call | External dependency; harder to test; breaks "single container" simplicity |
| Spring Batch | Overkill for a single daily task |   
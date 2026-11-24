# ADR-025: Truth & Accuracy Telemetry

## Status
Proposed (2026-10-29)

## Context
The vault claims that accuracy increases over time. That claim must be measurable — otherwise it is a slogan. The user needs a way to see the health of their vault: where it is uncertain, what needs attention, what is stale.

## Decision
Introduce a lightweight telemetry layer, surfaced on the dashboard and via an API.

### Metrics (computed on demand + cached)
| Metric | Definition |
|---|---|
| Facts by confidence bucket | count of facts in 0-.6, .6-.8, .8-.95, .95-1.0 |
| Open contradictions | count of `claim_conflict` where status=OPEN (ADR-020) |
| Stale facts | facts whose `source_doc_id` is deleted or `last_confirmed_at` older than N days |
| Pages needing review | count of `wiki_page.needs_review = true` |
| Coverage | facts per entity type; pages per type |
| Correction rate | corrections in last 7/30 days, by schema |
| Vault freshness | `last_compiled_at` distribution; oldest page |
| Answer tier mix | % of chat answers served by FACT / WIKI / RAG (ADR-023) |

### Surfaces
1. Dashboard panel (`DashboardController`) — tiles for open contradictions, needs-review pages, stale facts, correction backlog.
2. `GET /api/telemetry/summary` — JSON for the same data, useful for tests and future charts.
3. Per-document view — which facts a doc contributed, their confidence, whether any are under conflict.

### Computation
All metrics are simple SQL aggregates over `fact`, `claim_conflict`, `wiki_page`, `correction`. Computed on request (cheap at single-user scale) and memoised for 60 seconds.

### No external telemetry
Consistent with ADR-001: no metrics leave the machine. This is self-telemetry for the user.

## Consequences
- Plus: "Accuracy compounds" becomes observable and demonstrable
- Plus: User knows where to spend correction effort
- Plus: Answer tier mix proves the router is working (ADR-023)
- Plus: Cheap to build — mostly SQL + a Thymeleaf fragment
- Minus: More dashboard surface to maintain; acceptable
- Minus: Metrics can be gamed by the user; not a concern for single-user

## Alternatives Considered
| Option | Why rejected |
|---|---|
| No telemetry | Cannot verify the core promise |
| External analytics (PostHog etc.) | Violates local-first NFR |
| Full BI tooling | Overkill for single-user |
| Logs only | Not user-facing; no at-a-glance health |

## Related
ADR-001 (local-first), ADR-020 (conflicts), ADR-021 (confidence), ADR-023 (tier mix)

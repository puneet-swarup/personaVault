# ADR-014: Notification Channel Abstraction (Strategy Pattern)

## Status
Accepted (2026-09-20)

## Context
v0.2.0 delivers in-app notifications only. The user explicitly wants the ability to extend to email, SMS, or push notifications later without redesigning the notification system. The notification creation logic (what to say, when to say it) is separate from the delivery mechanism (where to send it).

## Decision
Define a `NotificationChannel` interface with `type()` and `send(Notification)`. The `NotificationService` injects all `NotificationChannel` beans (via `List<NotificationChannel>`) and fans out to each. Adding a new channel = one new `@Component` class. Zero changes to existing code.

Channels can be conditionally enabled via `@ConditionalOnProperty` (e.g., email channel only active if `personavault.notifications.email.enabled=true`).

## Consequences
- ✅ Open/Closed: new channels added without modifying existing code
- ✅ Channels independently testable
- ✅ Feature-flag channels via config (no code change to enable/disable)
- ✅ In-app channel is the canonical record (always created first)
- ❌ All channels receive every notification (no per-channel filtering in v1)
- ❌ No delivery tracking per channel in v1 (Phase 4: `notification_deliveries` table)
- ❌ If a channel throws, it blocks subsequent channels (mitigated: wrap each in try-catch in Phase 4)

## Alternatives Considered
| Option | Why rejected |
|---|---|
| Hardcoded if/else in NotificationService | Can't add channels without modifying the service; violates Open/Closed |
| Spring Events (`ApplicationEventPublisher`) | Decoupled but loses ordering guarantee; harder to test; over-abstracted for 1-3 channels |
| Message queue (RabbitMQ) | Massive overkill for single-user, 1-3 channels |
| No abstraction (just save to DB) | Works for v1 but makes Phase 4 email/SMS a refactor instead of an addition |   
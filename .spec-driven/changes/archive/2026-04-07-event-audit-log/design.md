# Design: event-audit-log

## Approach

Follow the same Lealone DB persistence pattern established by `LealoneSessionStore`:

1. **AuditEntry record** — wraps an `Event` with an auto-generated `id` (long) and the insertion timestamp. Stored in a single `audit_log` table.
2. **AuditLogStore interface** — defines `save(Event)`, `query(EventType, long from, long to)`, `queryBySource(String source, long from, long to)`, `deleteOlderThan(long cutoff)`, and `count()`.
3. **LealoneAuditLogStore** — creates the `audit_log` table on init, subscribes itself to `EventBus` (all `EventType` values) on construction, and writes each published event to the DB. Uses `Event.toJson()` for metadata persistence. Background VirtualThread runs periodic TTL cleanup.
4. **Integration** — constructed alongside `SimpleEventBus` in application wiring; no changes to existing classes.

## Key Decisions

- **Single-table design** — `audit_log` stores one row per event with columns for type, source, timestamp, and a CLOB for the full event JSON. Simpler than normalizing metadata into a separate table.
- **Subscribe to all types** — the store subscribes individually to every `EventType` enum value. When new types are added, the store re-subscribes on next initialization.
- **Reuse Event.toJson()** — the existing JSON serialization is already spec'd for round-trip correctness, so storing the full event as a CLOB avoids schema migration when `Event` fields change.
- **TTL cleanup** — like `LealoneSessionStore`, a background thread deletes entries older than a configurable retention period (default 30 days).

## Alternatives Considered

- **Separate metadata table** — normalized key-value storage for queryable metadata. Rejected: adds join complexity with no current use case; full JSON CLOB is sufficient for audit retrieval.
- **Async write queue** — batch writes via a queue to reduce DB contention. Deferred: the current EventBus dispatches synchronously; if throughput becomes an issue, a queuing subscriber can wrap the store later without interface changes.
- **EventBus interceptor instead of subscriber** — intercept events before dispatch. Rejected: audit is an observer, not a mediator; subscriber semantics match the use case correctly.

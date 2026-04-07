# Tasks: event-audit-log

## Implementation

- [x] Create `AuditEntry` record in `org.specdriven.agent.event` with fields: `id` (long), `event` (Event)
- [x] Create `AuditLogStore` interface in `org.specdriven.agent.event` with methods: `save(Event)`, `query(EventType, long from, long to)`, `queryBySource(String, long, long)`, `deleteOlderThan(long)`, `count()`
- [x] Implement `LealoneAuditLogStore` in `org.specdriven.agent.event` with `audit_log` table auto-creation, EventBus subscription, JDBC persistence, and background TTL cleanup
- [x] Update `event-system.md` delta spec with AuditLogStore and AuditEntry requirements

## Testing

- [x] Lint / validate: `mvn compile`
- [x] Run unit tests: `mvn test`
- [x] Unit test: `LealoneAuditLogStore` save and retrieve a single event
- [x] Unit test: `LealoneAuditLogStore` query by EventType and time range returns matching entries
- [x] Unit test: `LealoneAuditLogStore` queryBySource returns matching entries
- [x] Unit test: `LealoneAuditLogStore` query with no matches returns empty list
- [x] Unit test: `LealoneAuditLogStore` deleteOlderThan removes entries and returns count
- [x] Unit test: `LealoneAuditLogStore` count returns total entry count
- [x] Unit test: `AuditEntry` record holds id and event correctly

## Verification

- [x] All tests pass via `mvn test`
- [x] Delta spec accurately reflects implemented behavior
- [x] No modifications to existing EventBus/Event/EventType APIs

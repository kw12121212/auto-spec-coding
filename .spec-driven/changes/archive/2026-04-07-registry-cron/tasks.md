# Tasks: registry-cron

## Implementation

- [x] Implement `CronStatus` enum with states: ACTIVE, CANCELLED, FIRED
- [x] Implement `CronEntry` record with fields: id, name, cronExpression, delayMillis, status, prompt, metadata, createdAt, updatedAt, nextFireTime, lastFireTime
- [x] Implement `CronExpression` parser supporting standard 5-field format (M H DoM Mon DoW) with `*`, ranges, steps, and lists
- [x] Implement `CronStore` interface with methods: create, load, cancel, list, queryByStatus
- [x] Implement `LealoneCronStore` with auto-create `cron_entries` table, MERGE INTO upsert, EventBus integration, and metadata serialization
- [x] Implement one-shot delayed task support (null cronExpression + delayMillis > 0)
- [x] Implement background scheduler VirtualThread that polls for due entries every second and fires callbacks
- [x] Implement background cleanup VirtualThread that removes CANCELLED entries older than 7 days

## Testing

- [x] Run lint/validation: `mvn compile` to verify all code compiles without errors
- [x] Run unit tests: `mvn test` to execute all unit tests including new CronStatusTest, CronExpressionTest, CronEntryTest, LealoneCronStoreTest
- [x] Implement `CronStatusTest` — verify enum values
- [x] Implement `CronExpressionTest` — verify parsing of `*`, ranges, steps, lists, and `nextFireTime` calculation
- [x] Implement `CronEntryTest` — verify record immutability and metadata defensive copy
- [x] Implement `LealoneCronStoreTest` — verify CRUD, recurring fire, one-shot fire, cancel, cleanup, EventBus events

## Verification

- [x] Verify all new types are in `org.specdriven.agent.registry` package
- [x] Verify `CRON_TRIGGERED` event is published with correct metadata (entryId)
- [x] Verify delta spec `cron-registry.md` matches implementation

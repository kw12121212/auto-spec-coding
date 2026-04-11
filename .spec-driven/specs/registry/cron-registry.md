# Cron Registry Spec

## ADDED Requirements

### Requirement: CronEntry record

- MUST be a Java record in `org.specdriven.agent.registry` with fields: `id` (String), `name` (String), `cronExpression` (String, nullable), `delayMillis` (long), `status` (CronStatus), `prompt` (String), `metadata` (Map<String, Object>), `createdAt` (long, epoch millis), `updatedAt` (long, epoch millis), `nextFireTime` (long, epoch millis), `lastFireTime` (long, epoch millis)
- MUST be immutable
- Compact constructor MUST defensively copy the metadata map and normalize null to empty map
- `id` MAY be null before first save; after save it MUST be a non-empty UUID string
- A recurring entry MUST have a non-null `cronExpression` and `delayMillis` of 0
- A one-shot entry MUST have a null `cronExpression` and `delayMillis` greater than 0

### Requirement: CronStatus enum

- MUST define states: ACTIVE, CANCELLED, FIRED
- Each state MUST be independently testable

### Requirement: CronStatus state transitions

- MUST enforce the following valid transitions: ACTIVE→CANCELLED, ACTIVE→FIRED
- MUST reject any transition not listed above by throwing IllegalStateException with a descriptive message
- CANCELLED and FIRED MUST be terminal states — no transition away from them is allowed
- Only one-shot entries MAY transition to FIRED (recurring entries stay ACTIVE)

### Requirement: CronExpression parser

- MUST parse standard 5-field cron format: minute hour day-of-month month day-of-week
- MUST support `*` (any value), ranges (`1-5`), steps (`*/5`), and lists (`1,3,5`)
- MUST provide a `nextFireTime(String expression, long afterTimestamp)` method returning the next epoch millis after the given timestamp
- MUST throw IllegalArgumentException for invalid expressions

### Requirement: CronStore interface

- MUST be a public interface in `org.specdriven.agent.registry`
- MUST define `create(CronEntry)` returning `String` (the entry ID); MUST generate a UUID if `entry.id()` is null; MUST compute and persist `nextFireTime`
- MUST define `load(String entryId)` returning `Optional<CronEntry>`
- MUST define `cancel(String entryId)` returning void — transitions status to CANCELLED
- MUST define `list()` returning `List<CronEntry>` — all non-cancelled entries ordered by createdAt ascending
- MUST define `queryByStatus(CronStatus)` returning `List<CronEntry>` — entries matching the given status

### Requirement: LealoneCronStore implementation

- MUST implement CronStore in `org.specdriven.agent.registry`
- MUST persist entries to a single `cron_entries` table in Lealone DB
- MUST auto-create the `cron_entries` table on first initialization if it does not exist
- MUST accept EventBus and JDBC URL as constructor parameters
- MUST serialize metadata using `com.lealone.orm.json.JsonObject`
- MUST use `MERGE INTO` for save operations (upsert pattern)
- MUST update `updatedAt` to current time on every save/update
- MUST preserve `createdAt` on updates — only set on initial creation
- MUST be a public class in `org.specdriven.agent.registry`

### Requirement: CronStore scheduler

- MUST start a background VirtualThread on initialization that polls for due ACTIVE entries every second
- For recurring entries (non-null cronExpression): MUST invoke the registered callback, update `lastFireTime` to current time, compute and persist `nextFireTime`, and publish a CRON_TRIGGERED event
- For one-shot entries (null cronExpression, delayMillis > 0): MUST invoke the callback when `nextFireTime` is reached, transition status to FIRED, update `lastFireTime`, and publish a CRON_TRIGGERED event
- Callback failures MUST be logged as warnings and MUST NOT disable the entry
- Recurring entries MUST continue to fire after a callback failure on their next scheduled time

### Requirement: CronStore EventBus integration

- MUST publish an Event with type CRON_TRIGGERED when an entry fires
- Event source MUST be "CronStore"
- Event metadata MUST include `entryId` key with the entry's id value
- Event metadata MUST include `entryName` key with the entry's name value
- EventBus publish failures MUST be logged as warnings and MUST NOT propagate exceptions to callers

### Requirement: CronStore background cleanup

- MUST start a background VirtualThread on initialization that deletes CANCELLED entries older than 7 days every hour
- Background cleanup failures MUST be logged as warnings and MUST NOT propagate exceptions to callers

### Requirement: CronStore query behavior

- `load` with non-existent ID MUST return `Optional.empty()`
- `list` with no entries MUST return an empty list (not null)
- `queryByStatus` with no matches MUST return an empty list (not null)
- `cancel` on a non-existent entry MUST throw NoSuchElementException
- `cancel` on a CANCELLED or FIRED entry MUST throw IllegalStateException

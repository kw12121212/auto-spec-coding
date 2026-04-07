# Design: registry-cron

## Approach

Follow the same patterns established by `LealoneTaskStore` and `LealoneTeamStore`:

1. **CronExpression parser** — Self-contained parser for standard 5-field cron (`M H DoM Mon DoW`). No external library. Supports `*`, ranges (`1-5`), steps (`*/5`), and lists (`1,3,5`).

2. **Records and enums** — `CronEntry` (immutable record), `CronStatus` (ACTIVE, CANCELLED, FIRED), mirroring the Task/Team pattern.

3. **CronStore interface** — Create, load, cancel, list, queryByStatus. One-shot tasks use a `delayMillis` field (cron expression is null for fire-once entries).

4. **LealoneCronStore** — Single `cron_entries` table, auto-created on init. Uses `MERGE INTO` for upsert. Background VirtualThread polls every second for due entries, executes the callback, and publishes `CRON_TRIGGERED` events. Separate cleanup VirtualThread removes CANCELLED entries older than 7 days.

5. **Callback model** — `CronStore` accepts a `Runnable` callback on creation. The store invokes it when the entry fires. For recurring entries, the callback is invoked on each trigger. For one-shot entries, the status transitions to FIRED after execution.

## Key Decisions

- **5-field cron only** — No 6-field (seconds) or 7-field (year). Matches the spec-coding-sdk Go reference.
- **One-shot via `delayMillis`** — A nullable `cronExpression` + non-zero `delayMillis` indicates a fire-once task. Avoids a separate type hierarchy.
- **Polling scheduler** — Background VirtualThread wakes every second, queries for due entries, fires callbacks. Simpler than maintaining a priority queue of scheduled futures.
- **No distributed coordination** — Single-process embedded mode only, matching the project scope.
- **Error handling** — Callback failures are logged as warnings and do not disable the entry. Recurring entries continue to fire on their next schedule.

## Alternatives Considered

- **`ScheduledExecutorService`** — Could use JDK's scheduler directly, but it doesn't integrate well with DB-persisted entries (would need to re-register on restart). Polling + DB is simpler for restart recovery.
- **Separate `OneShotStore`** — Considered a separate interface but rejected; the lifecycle (create → fire → cleanup) is identical, only the trigger mechanism differs.
- **Quartz scheduler** — Overkill for the project's embedded single-process scope. Adds a heavy dependency for minimal gain.

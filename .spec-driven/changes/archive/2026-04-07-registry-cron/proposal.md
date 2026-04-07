# registry-cron

## What

Implement a cron-based scheduled task registry with Lealone DB persistence. Supports recurring cron jobs and one-shot delayed tasks, with automatic triggering and error handling.

## Why

M7 (task + team registries) is complete. The next registry in the sequence is the cron registry (M8), which provides the scheduling infrastructure that agents need to execute tasks periodically or after a delay. Lealone does not include a cron scheduler — its `Scheduler` is a database event loop — so we implement cron expression parsing and scheduling using JDK facilities.

## Scope

- Cron expression parser (standard 5-field format)
- `CronEntry` record, `CronStatus` enum
- `CronStore` interface (create, load, cancel, list, query)
- `LealoneCronStore` implementation with DB persistence
- One-shot delayed tasks (fire-once) via the same `CronStore` interface
- Background VirtualThread scheduler for triggering due jobs
- EventBus integration (`CRON_TRIGGERED` event)
- Background cleanup of cancelled entries older than 7 days

## Unchanged Behavior

- Existing `TaskStore`, `TeamStore`, `AuditLogStore` implementations must not change
- `EventType` enum already includes `CRON_TRIGGERED` — no modification needed
- Event system spec unchanged

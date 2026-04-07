# event-audit-log

## What

Add a persistent audit log layer that subscribes to the existing `EventBus` and writes every published `Event` to Lealone DB. Provide a query interface (`AuditLogStore`) for retrieving audit entries by event type, time range, and source — enabling debugging, compliance review, and behavior tracing across sessions.

## Why

M4's agent lifecycle, orchestrator, and session store are complete, but all event flow is ephemeral (in-memory `SimpleEventBus`). Once the process exits, there is no record of what happened. A persistent audit log closes this gap and completes M4. It also establishes the Lealone DB persistence pattern that M7 (registries) and M8 (cron) will follow.

## Scope

- `AuditLogStore` interface — query contract for audit entries
- `LealoneAuditLogStore` implementation — Lealone DB table, EventBus subscriber, query methods
- `AuditEntry` record — persisted representation of an `Event` with auto-generated ID
- Background TTL cleanup for old audit entries
- Delta spec for `event-system.md`

## Unchanged Behavior

- Existing `EventBus`, `SimpleEventBus`, and `Event` APIs remain unchanged
- `EventType` enum is not modified
- Event JSON serialization round-trip behavior is not affected
- Session store, orchestrator, and tool execution are not modified

# registry-tasks

## What

Implement a task registry with Lealone DB persistence, providing CRUD operations and state transitions for tasks. Includes `Task` record, `TaskStatus` enum, `TaskStore` interface, and `LealoneTaskStore` implementation following the established Lealone persistence patterns from `LealoneSessionStore` and `LealoneAuditLogStore`.

## Why

M7 (Task & Team Registries) is the next natural milestone after completing M1–M6. Task tracking is core agent infrastructure — the `EventType` enum already defines `TASK_CREATED` and `TASK_COMPLETED`, indicating this was planned from the start. Completing `registry-tasks` unblocks M8 (Cron Registry) which depends on M7's registry patterns, and provides the foundation for `registry-teams` (the second M7 change).

## Scope

- `Task` record with fields: id, title, description, status, owner, parentTaskId, metadata, createdAt, updatedAt
- `TaskStatus` enum: PENDING, IN_PROGRESS, COMPLETED, DELETED
- `TaskStore` interface: create, load, update, delete, list, queryByStatus, queryByOwner
- `LealoneTaskStore` implementation with Lealone DB persistence
- EventBus integration: publish TASK_CREATED on create, TASK_COMPLETED on status → COMPLETED
- Unit tests covering CRUD, state transitions, and edge cases

## Unchanged Behavior

- Existing EventType values (TASK_CREATED, TASK_COMPLETED) remain unchanged — only used
- EventBus interface and SimpleEventBus implementation unchanged
- AgentContext, SessionStore, AuditLogStore, PolicyStore all unchanged
- Task-to-team association is out of scope (deferred to `registry-teams`)

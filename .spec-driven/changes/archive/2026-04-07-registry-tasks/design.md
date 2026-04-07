# Design: registry-tasks

## Approach

Follow the established Lealone DB persistence pattern used by `LealoneSessionStore`, `LealoneAuditLogStore`, and `LealonePolicyStore`:

1. Define `Task` record and `TaskStatus` enum in a new `org.specdriven.agent.registry` package
2. Define `TaskStore` interface with CRUD + query operations
3. Implement `LealoneTaskStore` with a single `tasks` table, EventBus integration, and background cleanup for DELETED tasks older than retention period
4. Use `MERGE INTO` for upsert, `PreparedStatement` for queries, `JsonObject` for metadata serialization — matching existing patterns

### Table Schema

```sql
CREATE TABLE IF NOT EXISTS tasks (
    id          VARCHAR(36)  PRIMARY KEY,
    title       VARCHAR(500) NOT NULL,
    description CLOB,
    status      VARCHAR(20)  NOT NULL,
    owner       VARCHAR(255),
    parent_task VARCHAR(36),
    metadata    CLOB,
    created_at  BIGINT       NOT NULL,
    updated_at  BIGINT       NOT NULL
)
```

### State Machine

```
PENDING → IN_PROGRESS → COMPLETED
   ↓          ↓
 DELETED    DELETED
```

Valid transitions: PENDING→IN_PROGRESS, PENDING→DELETED, IN_PROGRESS→COMPLETED, IN_PROGRESS→DELETED. COMPLETED and DELETED are terminal states.

## Key Decisions

- **`owner` as String** — Teams don't exist yet (M7 `registry-teams`). Owner is a free-form string (agent ID, user ID, or team name once teams exist). Can be null for unassigned tasks.
- **`parentTaskId` for hierarchy** — Simple parent-child relationship without full tree traversal. Supports task breakdown patterns without over-engineering.
- **`metadata` as JSON CLOB** — Follows `LealonePolicyStore` pattern using `JsonObject` for flexible key-value data. Avoids schema changes for new task attributes.
- **DELETED is soft-delete** — `delete()` transitions status to DELETED rather than removing the row. Background cleanup removes DELETED rows older than 7 days. Matches audit trail expectations.
- **EventBus integration in constructor** — Same pattern as `LealoneAuditLogStore`: the store accepts an `EventBus` and publishes events on task state changes.
- **New package `org.specdriven.agent.registry`** — Keeps registry code separate from agent lifecycle, events, and permissions. M7's `registry-teams` and M8's `registry-cron` will go in the same package.

## Alternatives Considered

- **In-memory TaskStore with no persistence** — Rejected; Lealone DB persistence is a core project goal and the pattern is already established. In-memory would not survive restarts.
- **Single registry interface for tasks + teams** — Rejected; tasks and teams have fundamentally different shapes and lifecycles. Separate stores following the same pattern is cleaner and aligns with the milestone's two planned changes.
- **Full task dependency graph** — Rejected as over-engineering for the current scope. `parentTaskId` covers the basic parent-child case. A full DAG can be added later if needed.

# Tasks: registry-tasks

## Implementation

- [x] Create `TaskStatus` enum in `org.specdriven.agent.registry` with PENDING, IN_PROGRESS, COMPLETED, DELETED
- [x] Create `Task` record in `org.specdriven.agent.registry` with defensive copy in compact constructor
- [x] Add `TaskStatus.transition(TaskStatus from, TaskStatus to)` static validation method throwing IllegalStateException for invalid transitions
- [x] Create `TaskStore` interface in `org.specdriven.agent.registry` with save, load, update (two overloads), delete, list, queryByStatus, queryByOwner
- [x] Implement `LealoneTaskStore` constructor accepting EventBus and JDBC URL, initializing tasks table
- [x] Implement `LealoneTaskStore.save()` with UUID generation, MERGE INTO, and EventBus TASK_CREATED publish
- [x] Implement `LealoneTaskStore.load()` returning Optional<Task>
- [x] Implement `LealoneTaskStore.update()` overloads with status transition validation and updatedAt update
- [x] Implement `LealoneTaskStore.delete()` with PENDING/IN_PROGRESS → DELETED transition
- [x] Implement `LealoneTaskStore.list()`, `queryByStatus()`, `queryByOwner()` query methods
- [x] Implement background cleanup VirtualThread for DELETED tasks older than 7 days
- [x] Implement metadata serialization/deserialization using `com.lealone.orm.json.JsonObject`

## Testing

- [x] Validation: run `mvn compile` to verify all new code compiles without errors
- [x] Unit test: `TaskTest` — record construction, defensive copy, null normalization
- [x] Unit test: `TaskStatusTest` — all valid transitions, all invalid transitions throw IllegalStateException, terminal states reject further transitions
- [x] Unit test: `LealoneTaskStoreTest` — full CRUD lifecycle: create → load → update status → update title/description → delete → verify DELETED
- [x] Unit test: edge cases — load non-existent returns empty, delete non-existent throws, update non-existent throws, list/query with no tasks returns empty list
- [x] Unit test: EventBus integration — verify TASK_CREATED event published on save, TASK_COMPLETED event published on status → COMPLETED
- [x] Unit test: metadata serialization round-trip (Map → JSON → Map)
- [x] Run `mvn test -pl . -Dtest=org.specdriven.agent.registry.*` to execute all unit tests

## Verification

- [x] Verify implementation matches proposal scope (no team association, no full DAG)
- [x] Verify all Lealone DB patterns match existing stores (connection, table creation, upsert, cleanup thread)
- [x] Verify TaskStatus transitions match the state machine defined in design.md

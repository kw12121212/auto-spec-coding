# Task Registry Spec

## ADDED Requirements

### Requirement: Task record

- MUST be a Java record in `org.specdriven.agent.registry` with fields: `id` (String), `title` (String), `description` (String), `status` (TaskStatus), `owner` (String), `parentTaskId` (String), `metadata` (Map<String, Object>), `createdAt` (long, epoch millis), `updatedAt` (long, epoch millis)
- MUST be immutable
- Compact constructor MUST defensively copy the metadata map and normalize null to empty map
- `id` MAY be null before first save; after save it MUST be a non-empty UUID string

### Requirement: TaskStatus enum

- MUST define states: PENDING, IN_PROGRESS, COMPLETED, DELETED
- Each state MUST be independently testable

### Requirement: Task state transitions

- MUST enforce the following valid transitions: PENDING→IN_PROGRESS, PENDING→DELETED, IN_PROGRESS→COMPLETED, IN_PROGRESS→DELETED
- MUST reject any transition not listed above by throwing IllegalStateException with a descriptive message
- COMPLETED and DELETED MUST be terminal states — no transition away from them is allowed

### Requirement: TaskStore interface

- MUST be a public interface in `org.specdriven.agent.registry`
- MUST define `save(Task)` returning `String` (the task ID); MUST generate a UUID if `task.id()` is null
- MUST define `load(String taskId)` returning `Optional<Task>`
- MUST define `update(String taskId, TaskStatus newStatus)` returning `Task` — the updated task with new status and updatedAt
- MUST define `update(String taskId, String title, String description)` returning `Task` — the updated task with new title/description and updatedAt
- MUST define `delete(String taskId)` returning void — transitions status to DELETED
- MUST define `list()` returning `List<Task>` — all non-deleted tasks ordered by createdAt ascending
- MUST define `queryByStatus(TaskStatus)` returning `List<Task>` — non-deleted tasks matching the given status
- MUST define `queryByOwner(String owner)` returning `List<Task>` — non-deleted tasks matching the given owner

### Requirement: LealoneTaskStore implementation

- MUST implement TaskStore in `org.specdriven.agent.registry`
- MUST persist tasks to a single `tasks` table in Lealone DB
- MUST auto-create the `tasks` table on first initialization if it does not exist
- MUST accept EventBus and JDBC URL as constructor parameters
- MUST serialize metadata using `com.lealone.orm.json.JsonObject`
- MUST use `MERGE INTO` for save operations (upsert pattern)
- MUST update `updatedAt` to current time on every save/update
- MUST preserve `createdAt` on updates — only set on initial creation
- MUST be a public class in `org.specdriven.agent.registry`

### Requirement: TaskStore EventBus integration

- MUST publish an Event with type TASK_CREATED when a new task is saved (id was null → generated)
- MUST publish an Event with type TASK_COMPLETED when a task transitions to COMPLETED status
- Event source MUST be "TaskStore"
- Event metadata MUST include `taskId` key with the task's id value
- EventBus publish failures MUST be logged as warnings and MUST NOT propagate exceptions to callers

### Requirement: TaskStore background cleanup

- MUST start a background VirtualThread on initialization that deletes DELETED tasks older than 7 days every hour
- Background cleanup failures MUST be logged as warnings and MUST NOT propagate exceptions to callers

### Requirement: TaskStore query behavior

- `load` with non-existent ID MUST return `Optional.empty()`
- `list` with no tasks MUST return an empty list (not null)
- `queryByStatus` with no matches MUST return an empty list (not null)
- `queryByOwner` with no matches MUST return an empty list (not null)
- `delete` on a non-existent task MUST throw NoSuchElementException
- `update` on a non-existent task MUST throw NoSuchElementException

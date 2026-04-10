# Delta Spec: autonomous-loop.md

## ADDED Requirements

### Requirement: LoopIterationStore interface

- MUST be a public interface in `org.specdriven.agent.loop`
- MUST define `saveIteration(LoopIteration)` returning void — persists a completed iteration record
- MUST define `loadIterations()` returning `List<LoopIteration>` ordered by iterationNumber ascending
- MUST define `saveProgress(LoopProgress)` returning void — persists the current loop-level progress snapshot
- MUST define `loadProgress()` returning `Optional<LoopProgress>` — returns empty when no prior progress exists
- MUST define `clear()` returning void — removes all stored iterations and progress (for test cleanup)

### Requirement: LoopProgress record

- MUST be a Java record in `org.specdriven.agent.loop` with fields: `loopState` (LoopState), `completedChangeNames` (Set<String>), `totalIterations` (int)
- MUST be immutable
- Compact constructor MUST defensively copy `completedChangeNames`, normalizing null to empty set
- `totalIterations` MUST be non-negative

### Requirement: LealoneLoopIterationStore

- MUST implement `LoopIterationStore` in `org.specdriven.agent.loop`
- Constructor MUST accept `EventBus` and `String jdbcUrl`
- MUST auto-create `loop_iterations` and `loop_progress` tables on construction using `CREATE TABLE IF NOT EXISTS`
- `saveIteration()` MUST merge the iteration record into `loop_iterations` keyed by `iteration_number`
- `loadIterations()` MUST return all rows from `loop_iterations` ordered by `iteration_number ASC`, mapping SQL columns to `LoopIteration` record fields
- `saveProgress()` MUST merge a single row into `loop_progress` (fixed id=1), serializing `completedChangeNames` as a JSON array string
- `loadProgress()` MUST deserialize the JSON array back to `Set<String>`, reconstructing the `LoopProgress` record
- `clear()` MUST delete all rows from both tables
- JDBC exceptions MUST be wrapped in `IllegalStateException` with descriptive messages
- MUST use `DriverManager.getConnection(jdbcUrl, "root", "")` for connections, consistent with existing Lealone Store implementations
- EventBus publish failures MUST be logged as warnings and MUST NOT propagate exceptions

### Requirement: DefaultLoopDriver persistence integration

- MUST add constructor `DefaultLoopDriver(LoopConfig, LoopScheduler, LoopPipeline, LoopIterationStore)` accepting a store instance
- When a non-null store is provided:
  - `start()` MUST call `store.loadProgress()` to recover `completedChangeNames` before entering the scheduling loop
  - After each iteration completes, MUST call `store.saveIteration(completedIteration)` and `store.saveProgress(currentSnapshot)`
  - On `stop()`, MUST call `store.saveProgress(finalSnapshot)` with the terminal state
- When store is null (existing constructors), MUST continue using in-memory tracking only — no behavioral change
- Recovered `completedChangeNames` MUST be passed to `LoopContext` so `SequentialMilestoneScheduler` skips already-completed changes

### Requirement: LOOP_PROGRESS_SAVED EventType

- MUST add `LOOP_PROGRESS_SAVED` to the existing `EventType` enum in `org.specdriven.agent.event`
- Existing EventType values MUST NOT change
- `DefaultLoopDriver` MUST publish this event after each successful `saveProgress()` call with metadata: `iterationCount` (int), `completedChangeCount` (int)

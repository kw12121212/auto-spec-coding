# Tasks: loop-progress-persistence

## Implementation

- [x] Create `LoopProgress` record in `org.specdriven.agent.loop` with fields `loopState`, `completedChangeNames`, `totalIterations`, defensive copy and validation
- [x] Create `LoopIterationStore` interface in `org.specdriven.agent.loop` with methods `saveIteration`, `loadIterations`, `saveProgress`, `loadProgress`, `clear`
- [x] Implement `LealoneLoopIterationStore` with `loop_iterations` and `loop_progress` table DDL, CRUD operations, JSON serialization for `completedChangeNames`
- [x] Add `LOOP_PROGRESS_SAVED` value to `EventType` enum in `org.specdriven.agent.event`
- [x] Add four-argument constructor to `DefaultLoopDriver(LoopConfig, LoopScheduler, LoopPipeline, LoopIterationStore)` with persistence logic in `start()`, iteration completion, and `stop()`
- [x] Wire `DefaultLoopDriver` to load progress on start, pass recovered `completedChangeNames` to scheduler via `LoopContext`, save iteration and progress after each loop cycle

## Testing

- [x] Run `mvn compile -pl . -q` to verify compilation
- [x] Create `LoopProgressTest` — unit tests for record validation, defensive copy, null normalization
- [x] Create `LealoneLoopIterationStoreTest` — integration tests covering save/load iterations, save/load progress, clear, empty store returns empty/optional-empty, JSON round-trip for change names
- [x] Create or extend `DefaultLoopDriverTest` — tests for persistence integration: start with prior progress recovers completed changes, iteration completion persists to store, stop persists final snapshot, null store falls back to in-memory behavior

## Verification

- [x] Run `mvn test -pl . -Dtest="LoopProgressTest,LealoneLoopIterationStoreTest,DefaultLoopDriverTest"` — all pass
- [x] Verify `LoopDriver` interface is unchanged (no method signature changes)
- [x] Verify existing two-arg and three-arg `DefaultLoopDriver` constructors still work without a store
- [x] Verify delta spec `autonomous-loop.md` reflects only ADDED requirements

# Tasks: loop-driver-core

## Implementation

- [x] Add `LoopState` enum with state transition validation (IDLE, RECOMMENDING, RUNNING, CHECKPOINT, PAUSED, STOPPED, ERROR)
- [x] Add `LoopConfig` record with defaults factory (maxIterations=10, iterationTimeoutSeconds=600, targetMilestones=empty, projectRoot, eventBus)
- [x] Add `IterationStatus` enum (SUCCESS, FAILED, SKIPPED, TIMED_OUT)
- [x] Add `LoopIteration` record (iterationNumber, changeName, milestoneFile, startedAt, completedAt, status, failureReason)
- [x] Add `PlannedChange` and `LoopCandidate` records
- [x] Add `LoopContext` record
- [x] Add `LoopDriver` interface (start, pause, resume, stop, getState, getCurrentIteration, getCompletedIterations, getConfig)
- [x] Add `LoopScheduler` interface (selectNext)
- [x] Add `SequentialMilestoneScheduler` implementation — parse roadmap INDEX.md and milestone files from disk on every selectNext(), filter by targetMilestones if non-empty, select next planned change in order
- [x] Add `DefaultLoopDriver` implementation — VirtualThread scheduling loop, synchronized state transitions, safety limit checks, EventBus publication
- [x] Add loop event types to `EventType` enum (LOOP_STARTED, LOOP_PAUSED, LOOP_RESUMED, LOOP_STOPPED, LOOP_ITERATION_COMPLETED, LOOP_ERROR)

## Testing

- [x] Run `mvn compile -q` — lint/compile validation
- [x] Run `mvn test -pl . -Dtest="LoopStateTest,LoopConfigTest,LoopIterationTest,SequentialMilestoneSchedulerTest,DefaultLoopDriverTest"` — unit tests covering state transitions, config validation, scheduling order, safety limits, and event publication

## Verification

- [x] Verify all loop state transitions are validated (valid and invalid cases)
- [x] Verify SequentialMilestoneScheduler selects changes in correct order and skips completed ones
- [x] Verify DefaultLoopDriver publishes events on each state transition
- [x] Verify safety limits (maxIterations, timeout) trigger automatic stop

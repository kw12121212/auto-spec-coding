# Autonomous Loop Driver

## ADDED Requirements

### Requirement: LoopState enum

- MUST be a public enum in `org.specdriven.agent.loop` with states: IDLE, RECOMMENDING, RUNNING, CHECKPOINT, PAUSED, STOPPED, ERROR
- MUST enforce valid state transitions:
  - IDLE → RECOMMENDING
  - RECOMMENDING → RUNNING, PAUSED, STOPPED, ERROR
  - RUNNING → CHECKPOINT, PAUSED, STOPPED, ERROR
  - CHECKPOINT → RECOMMENDING, PAUSED, STOPPED, ERROR
  - PAUSED → RECOMMENDING
  - ERROR → IDLE
- MUST reject any transition not listed above by throwing `IllegalStateException` with a descriptive message
- STOPPED MUST be a terminal state — no transition away from it is allowed

### Requirement: LoopConfig record

- MUST be a Java record in `org.specdriven.agent.loop` with fields: `maxIterations` (int), `iterationTimeoutSeconds` (int), `targetMilestones` (List<String>), `projectRoot` (Path), `eventBus` (EventBus)
- MUST be immutable
- Compact constructor MUST reject null `projectRoot` and null `eventBus` with `NullPointerException`
- Compact constructor MUST defensively copy `targetMilestones`, normalizing null to empty list
- `maxIterations` MUST be positive; compact constructor MUST reject non-positive values with `IllegalArgumentException`
- `iterationTimeoutSeconds` MUST be positive
- `targetMilestones` MAY be empty; when empty the loop scans all milestones; when non-empty the loop only considers the specified milestone file names
- MUST provide static factory `defaults(Path projectRoot, EventBus eventBus)` returning a LoopConfig with maxIterations=10, iterationTimeoutSeconds=600, and empty targetMilestones

### Requirement: IterationStatus enum

- MUST be a public enum in `org.specdriven.agent.loop` with values: SUCCESS, FAILED, SKIPPED, TIMED_OUT
- Each value MUST be independently testable

### Requirement: LoopIteration record

- MUST be a Java record in `org.specdriven.agent.loop` with fields: `iterationNumber` (int), `changeName` (String), `milestoneFile` (String), `startedAt` (long), `completedAt` (Long, nullable), `status` (IterationStatus), `failureReason` (String, nullable)
- MUST be immutable
- `iterationNumber` MUST be positive (≥1)
- `completedAt` MUST be null while iteration is in progress; non-null when iteration is complete

### Requirement: PlannedChange record

- MUST be a Java record in `org.specdriven.agent.loop` with fields: `name` (String), `status` (String), `summary` (String)
- MUST be immutable
- Compact constructor MUST reject null `name` with `NullPointerException`

### Requirement: LoopCandidate record

- MUST be a Java record in `org.specdriven.agent.loop` with fields: `changeName` (String), `milestoneFile` (String), `milestoneGoal` (String)
- MUST be immutable
- Compact constructor MUST reject null `changeName` and null `milestoneFile` with `NullPointerException`

### Requirement: LoopContext record

- MUST be a Java record in `org.specdriven.agent.loop` with fields: `milestoneFile` (String), `milestoneGoal` (String), `plannedChanges` (List<PlannedChange>), `completedChangeNames` (Set<String>)
- MUST be immutable
- Compact constructor MUST defensively copy `plannedChanges` and `completedChangeNames`, normalizing null to empty

### Requirement: LoopDriver interface

- MUST be a public interface in `org.specdriven.agent.loop`
- MUST define `start()` returning void — transitions IDLE → RECOMMENDING; MUST throw `IllegalStateException` if current state is not IDLE; MUST publish LOOP_STARTED event
- MUST define `pause()` returning void — transitions from RECOMMENDING/RUNNING/CHECKPOINT to PAUSED; MUST publish LOOP_PAUSED event
- MUST define `resume()` returning void — transitions PAUSED → RECOMMENDING; MUST publish LOOP_RESUMED event
- MUST define `stop()` returning void — transitions any non-STOPPED state to STOPPED; MUST publish LOOP_STOPPED event with metadata `totalIterations` and `reason`
- MUST define `getState()` returning `LoopState`
- MUST define `getCurrentIteration()` returning `Optional<LoopIteration>`
- MUST define `getCompletedIterations()` returning `List<LoopIteration>` ordered by iterationNumber ascending
- MUST define `getConfig()` returning `LoopConfig`

### Requirement: LoopScheduler interface

- MUST be a public interface in `org.specdriven.agent.loop`
- MUST define `selectNext(LoopContext)` returning `Optional<LoopCandidate>`
- Returns empty when no candidate is available

### Requirement: SequentialMilestoneScheduler

- MUST implement LoopScheduler in `org.specdriven.agent.loop`
- MUST read and parse roadmap INDEX.md and milestone files from disk on every `selectNext()` call
- When `LoopContext` is constructed with a non-empty target filter (from `LoopConfig.targetMilestones`), MUST only consider milestones matching the filter
- MUST select the first non-complete milestone from the eligible set
- Within a milestone, MUST select the first planned change not in `completedChangeNames`
- MUST skip milestones with declared status `complete`
- MUST skip changes with status `complete` or already in `completedChangeNames`
- MUST return `Optional.empty()` when all milestones have no eligible changes

### Requirement: DefaultLoopDriver

- MUST implement LoopDriver in `org.specdriven.agent.loop`
- Constructor MUST accept `LoopConfig` and `LoopScheduler`
- Constructor MUST accept `LoopConfig`, `LoopScheduler`, and `LoopPipeline`
- A backward-compatible constructor `DefaultLoopDriver(LoopConfig, LoopScheduler)` MUST remain, using a `StubLoopPipeline` that returns `IterationResult` with `status=SUCCESS` and empty phases
- `start()` MUST launch a VirtualThread running the scheduling loop
- The scheduling loop MUST check `maxIterations` before each iteration; when reached, MUST call `stop()` with reason "max iterations reached"
- The scheduling loop MUST call `pipeline.execute(candidate, config)` during the RUNNING state
- The completed `LoopIteration` MUST use the `IterationResult.status` for its `status` field
- The completed `LoopIteration` MUST use the `IterationResult.failureReason` for its `failureReason` field when status is not SUCCESS
- `completedAt` MUST be set to the current time after `pipeline.execute()` returns, not before
- `stop()` MUST interrupt the running scheduling VirtualThread
- State transitions MUST be performed within a `synchronized` block
- MUST publish events to `config.eventBus()` after each state transition
- Event source MUST be "LoopDriver"
- EventBus publish failures MUST be logged as warnings and MUST NOT propagate exceptions to callers

### Requirement: Loop EventType additions

- MUST add the following values to the existing `EventType` enum in `org.specdriven.agent.event`: LOOP_STARTED, LOOP_PAUSED, LOOP_RESUMED, LOOP_STOPPED, LOOP_ITERATION_COMPLETED, LOOP_ERROR
- Existing EventType values MUST NOT change

### Requirement: PipelinePhase enum

- MUST be a public enum in `org.specdriven.agent.loop` with values: PROPOSE, IMPLEMENT, VERIFY, REVIEW, ARCHIVE
- Each value MUST provide a `templateResource()` method returning a `String` classpath path to its instruction template file
- Template resource paths MUST follow the pattern `/loop-phases/<lowercase-name>.txt`
- MUST provide a static `ordered()` method returning phases in execution order (PROPOSE → IMPLEMENT → VERIFY → REVIEW → ARCHIVE)

### Requirement: IterationResult record

- MUST be a Java record in `org.specdriven.agent.loop` with fields: `status` (IterationStatus), `failureReason` (String, nullable), `durationMs` (long), `phasesCompleted` (List<PipelinePhase>)
- MUST be immutable
- Compact constructor MUST defensively copy `phasesCompleted`, normalizing null to empty list
- `durationMs` MUST be non-negative

### Requirement: LoopPipeline interface

- MUST be a public interface in `org.specdriven.agent.loop`
- MUST define `execute(LoopCandidate candidate, LoopConfig config)` returning `IterationResult`
- Implementations MUST NOT throw checked exceptions — all failures MUST be captured in the returned `IterationResult` with appropriate `IterationStatus`

### Requirement: SpecDrivenPipeline

- MUST implement `LoopPipeline` in `org.specdriven.agent.loop`
- Constructor MUST accept a `Function<Path, LlmClient>` factory for creating LLM clients
- Constructor MUST accept a `Map<String, Tool>` tool registry
- Constructor MUST provide a convenience overload with default tools (bash, read, write, edit, glob, grep)
- `execute()` MUST iterate through `PipelinePhase.ordered()` sequentially
- For each phase, MUST load the instruction template from classpath, build user prompt, create Conversation, assemble SimpleAgentContext, run Orchestrator, track the phase as completed only if the orchestrator finishes without exception
- If any phase throws an exception, MUST stop execution and return an `IterationResult` with `status=FAILED` and `failureReason` describing which phase failed and why
- If timeout deadline is exceeded before a phase, MUST return `IterationResult` with `status=TIMED_OUT`
- If all phases complete, MUST return `IterationResult` with `status=SUCCESS`
- `durationMs` MUST reflect wall-clock time from start to finish of `execute()`

### Requirement: Phase instruction template resources

- MUST provide 5 text files under `src/main/resources/loop-phases/`: `propose.txt`, `implement.txt`, `verify.txt`, `review.txt`, `archive.txt`
- Each template MUST be valid UTF-8 text
- Each template MUST describe: the phase objective, expected inputs, expected outputs, and tool usage guidance
- Templates MUST support variable substitution for `${changeName}`, `${milestoneGoal}`, `${projectRoot}`

### Requirement: StubLoopPipeline

- MUST be a package-private class in `org.specdriven.agent.loop`
- MUST implement `LoopPipeline`
- `execute()` MUST return `IterationResult` with `status=SUCCESS`, `failureReason=null`, `durationMs=0`, `phasesCompleted=empty`
- Exists solely for backward compatibility with the two-arg `DefaultLoopDriver` constructor

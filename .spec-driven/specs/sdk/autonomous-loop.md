---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/event/EventType.java
    - src/main/java/org/specdriven/agent/loop/AnswerResolution.java
    - src/main/java/org/specdriven/agent/loop/ContextBudget.java
    - src/main/java/org/specdriven/agent/loop/DefaultLoopAnswerAgent.java
    - src/main/java/org/specdriven/agent/loop/DefaultLoopDriver.java
    - src/main/java/org/specdriven/agent/loop/InteractiveSessionFactory.java
    - src/main/java/org/specdriven/agent/loop/IterationResult.java
    - src/main/java/org/specdriven/agent/loop/IterationStatus.java
    - src/main/java/org/specdriven/agent/loop/LealoneLoopIterationStore.java
    - src/main/java/org/specdriven/agent/loop/LoopAnswerAgent.java
    - src/main/java/org/specdriven/agent/loop/LoopCandidate.java
    - src/main/java/org/specdriven/agent/loop/LoopConfig.java
    - src/main/java/org/specdriven/agent/loop/LoopContext.java
    - src/main/java/org/specdriven/agent/loop/LoopDriver.java
    - src/main/java/org/specdriven/agent/loop/LoopIteration.java
    - src/main/java/org/specdriven/agent/loop/LoopIterationStore.java
    - src/main/java/org/specdriven/agent/loop/LoopPipeline.java
    - src/main/java/org/specdriven/agent/loop/LoopProgress.java
    - src/main/java/org/specdriven/agent/loop/LoopScheduler.java
    - src/main/java/org/specdriven/agent/loop/LoopState.java
    - src/main/java/org/specdriven/agent/loop/PipelinePhase.java
    - src/main/java/org/specdriven/agent/loop/PlannedChange.java
    - src/main/java/org/specdriven/agent/loop/SequentialMilestoneScheduler.java
    - src/main/java/org/specdriven/agent/loop/SpecDrivenPipeline.java
    - src/main/java/org/specdriven/agent/loop/StubLoopPipeline.java
    - src/main/java/org/specdriven/agent/interactive/InteractiveSession.java
    - src/main/java/org/specdriven/agent/loop/TokenAccumulator.java
    - src/main/java/org/specdriven/agent/question/QuestionDeliveryService.java
    - src/main/resources/loop-phases/archive.txt
    - src/main/resources/loop-phases/implement.txt
    - src/main/resources/loop-phases/propose.txt
    - src/main/resources/loop-phases/recommend.txt
    - src/main/resources/loop-phases/review.txt
    - src/main/resources/loop-phases/verify.txt
  tests:
    - src/test/java/org/specdriven/agent/loop/ContextBudgetTest.java
    - src/test/java/org/specdriven/agent/loop/DefaultLoopAnswerAgentTest.java
    - src/test/java/org/specdriven/agent/loop/DefaultLoopDriverTest.java
    - src/test/java/org/specdriven/agent/loop/IterationResultTest.java
    - src/test/java/org/specdriven/agent/loop/LealoneLoopIterationStoreTest.java
    - src/test/java/org/specdriven/agent/loop/LoopConfigTest.java
    - src/test/java/org/specdriven/agent/loop/LoopIterationTest.java
    - src/test/java/org/specdriven/agent/loop/LoopProgressTest.java
    - src/test/java/org/specdriven/agent/loop/LoopStateTest.java
    - src/test/java/org/specdriven/agent/loop/PipelinePhaseTest.java
    - src/test/java/org/specdriven/agent/loop/SequentialMilestoneSchedulerTest.java
    - src/test/java/org/specdriven/agent/interactive/InteractiveSessionTest.java
    - src/test/java/org/specdriven/agent/loop/SpecDrivenPipelineTest.java
---

# Autonomous Loop Driver

## ADDED Requirements

### Requirement: LoopState enum

- MUST be a public enum in `org.specdriven.agent.loop` with states: IDLE, RECOMMENDING, RUNNING, CHECKPOINT, QUESTIONING, PAUSED, STOPPED, ERROR
- MUST enforce valid state transitions:
  - IDLE → RECOMMENDING
  - RECOMMENDING → RUNNING, PAUSED, STOPPED, ERROR
  - RUNNING → CHECKPOINT, QUESTIONING, PAUSED, STOPPED, ERROR
  - CHECKPOINT → RECOMMENDING, PAUSED, STOPPED, ERROR
  - QUESTIONING → RUNNING, PAUSED, ERROR
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

- MUST be a public enum in `org.specdriven.agent.loop` with values: SUCCESS, FAILED, SKIPPED, TIMED_OUT, QUESTIONING
- `QUESTIONING` indicates the iteration was interrupted because a Question was detected in the pipeline; answer routing is in progress
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

- MUST be a Java record in `org.specdriven.agent.loop` with fields: `changeName` (String), `milestoneFile` (String), `milestoneGoal` (String), `plannedChangeSummary` (String)
- MUST be immutable
- Compact constructor MUST reject null `changeName` and null `milestoneFile` with `NullPointerException`
- Compact constructor MUST normalize null `plannedChangeSummary` to an empty string
- MUST provide a backward-compatible constructor accepting only `changeName`, `milestoneFile`, and `milestoneGoal`, defaulting `plannedChangeSummary` to an empty string

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
- MUST skip changes whose status is not `planned` or already in `completedChangeNames`
- MUST include the selected planned change summary in the returned `LoopCandidate`
- MUST return `Optional.empty()` when all milestones have no eligible changes

### Requirement: Loop auto recommend phase

- The autonomous loop MUST include a first-class recommend phase before proposal work begins
- The recommend phase MUST run before the propose phase when an autonomous loop iteration has an eligible roadmap planned change
- The recommend phase order MUST be auditable as `RECOMMEND → PROPOSE → IMPLEMENT → VERIFY → REVIEW → ARCHIVE`
- Loop auto recommend MUST only select a roadmap planned change declared as `planned` and not already present in completed change names
- Loop auto recommend MUST NOT select completed milestones, completed changes, or changes already present in completed change names
- Loop auto recommend MUST preserve target milestone filtering
- The propose phase in the same iteration MUST use the same change name and milestone file selected by the recommend phase and MUST NOT reselect a different roadmap candidate

### Requirement: Loop-only no-confirm recommend path

- The autonomous loop MUST provide a recommend path that does not wait for human confirmation
- When an autonomous loop is running and at least one eligible roadmap planned change exists, the loop MUST be able to proceed from recommend to propose without a human confirmation checkpoint
- Manual roadmap recommendation workflows MUST NOT scaffold proposal artifacts until the user explicitly confirms the change name and scope

### Requirement: DefaultLoopDriver

- MUST implement LoopDriver in `org.specdriven.agent.loop`
- Constructor MUST accept `LoopConfig` and `LoopScheduler`
- Constructor MUST accept `LoopConfig`, `LoopScheduler`, and `LoopPipeline`
- Constructor MUST accept `LoopConfig`, `LoopScheduler`, `LoopPipeline`, `LoopIterationStore`, and `LoopAnswerAgent` (where `LoopAnswerAgent` MAY be null)
- Constructor MUST accept `LoopConfig`, `LoopScheduler`, `LoopPipeline`, `LoopIterationStore`, `LoopAnswerAgent`, and `QuestionDeliveryService` (where `QuestionDeliveryService` MAY be null)
- Existing constructors MUST behave as if no question delivery service is configured
- A backward-compatible constructor `DefaultLoopDriver(LoopConfig, LoopScheduler)` MUST remain, using a `StubLoopPipeline` that returns `IterationResult` with `status=SUCCESS` and empty phases
- `start()` MUST launch a VirtualThread running the scheduling loop
- The scheduling loop MUST check `maxIterations` before each iteration; when reached, MUST call `stop()` with reason "max iterations reached"
- The scheduling loop MUST call `pipeline.execute(candidate, config)` during the RUNNING state
- When the pipeline returns `status=QUESTIONING`:
  - MUST publish `LOOP_QUESTION_ROUTED` event with metadata: `questionId` (String), `changeName` (String), `sessionId` (String)
  - MUST transition `RUNNING → QUESTIONING`
  - MUST inspect the returned `Question` before invoking `LoopAnswerAgent`
  - If the question category is `PERMISSION_CONFIRMATION` or `IRREVERSIBLE_APPROVAL`, MUST NOT invoke `LoopAnswerAgent`
  - If the question delivery mode is `PUSH_MOBILE_WAIT_HUMAN` or `PAUSE_WAIT_HUMAN`, MUST NOT invoke `LoopAnswerAgent`
  - Human-escalated questions MUST publish `LOOP_QUESTION_ESCALATED`, transition `QUESTIONING → PAUSED`, record a partial `LoopIteration` with `status=QUESTIONING` and a non-empty `failureReason`, and wait for resume
  - Human-escalated questions MUST NOT add the paused change name to `completedChangeNames`
  - When a question delivery service is configured for a human-escalated question, MUST submit the waiting question to that service
  - When no question delivery service is configured, MUST still expose the escalation through loop event metadata
  - When `loopAnswerAgent` is non-null: MUST call `loopAnswerAgent.resolve(result.question(), config.iterationTimeoutSeconds())`
    - On `Resolved`: MUST publish `LOOP_QUESTION_ANSWERED` (metadata: questionId, changeName, confidence), transition `QUESTIONING → RUNNING`, then re-invoke `pipeline.execute(candidate, config, Set.copyOf(result.phasesCompleted()))` to resume from the interrupted phase
    - On `Escalated`: MUST publish `LOOP_QUESTION_ESCALATED`, transition `QUESTIONING → PAUSED`, record a partial `LoopIteration` with `status=QUESTIONING` and `failureReason=resolution.reason()`, wait for resume
  - When `loopAnswerAgent` is null: treat as `Escalated("no answer agent configured")`
- The completed `LoopIteration` MUST use the `IterationResult.status` for its `status` field
- The completed `LoopIteration` MUST use the `IterationResult.failureReason` for its `failureReason` field when status is not SUCCESS
- `completedAt` MUST be set to the current time after `pipeline.execute()` returns, not before
- `stop()` MUST interrupt the running scheduling VirtualThread
- State transitions MUST be performed within a `synchronized` block
- MUST publish events to `config.eventBus()` after each state transition
- Event source MUST be "LoopDriver"
- EventBus publish failures MUST be logged as warnings and MUST NOT propagate exceptions to callers

### Requirement: Loop EventType additions

- MUST add the following values to the existing `EventType` enum in `org.specdriven.agent.event`: LOOP_STARTED, LOOP_PAUSED, LOOP_RESUMED, LOOP_STOPPED, LOOP_ITERATION_COMPLETED, LOOP_QUESTION_ROUTED, LOOP_QUESTION_ANSWERED, LOOP_QUESTION_ESCALATED, LOOP_ERROR
- `LOOP_QUESTION_ROUTED` — published when a question is detected and routing begins; metadata: `questionId` (String), `changeName` (String), `sessionId` (String)
- `LOOP_QUESTION_ANSWERED` — published when `LoopAnswerAgent` returns `Resolved`; metadata: `questionId` (String), `changeName` (String), `confidence` (double)
- `LOOP_QUESTION_ESCALATED` — published when a question needs human handling, `LoopAnswerAgent` returns `Escalated`, or `LoopAnswerAgent` is absent; metadata: `questionId` (String), `sessionId` (String), `changeName` (String), `category` (String enum name), `deliveryMode` (String enum name), `reason` (String), `routingReason` (String)
- Existing EventType values MUST NOT change

### Requirement: PipelinePhase enum

- MUST be a public enum in `org.specdriven.agent.loop` with values: RECOMMEND, PROPOSE, IMPLEMENT, VERIFY, REVIEW, ARCHIVE
- Each value MUST provide a `templateResource()` method returning a `String` classpath path to its instruction template file
- Template resource paths MUST follow the pattern `/loop-phases/<lowercase-name>.txt`
- MUST provide a static `ordered()` method returning phases in execution order (RECOMMEND → PROPOSE → IMPLEMENT → VERIFY → REVIEW → ARCHIVE)

### Requirement: IterationResult record

- MUST be a Java record in `org.specdriven.agent.loop` with fields: `status` (IterationStatus), `failureReason` (String, nullable), `durationMs` (long), `phasesCompleted` (List<PipelinePhase>), `tokenUsage` (long), `question` (Question, nullable)
- MUST be immutable
- Compact constructor MUST defensively copy `phasesCompleted`, normalizing null to empty list
- `durationMs` MUST be non-negative
- `tokenUsage` MUST be non-negative; compact constructor MUST reject negative values with `IllegalArgumentException`
- `question` MUST be non-null when `status == QUESTIONING`; MUST be null for all other statuses
- Backward-compatible 5-arg constructor `(status, failureReason, durationMs, phasesCompleted, tokenUsage)` defaults `question` to null
- Backward-compatible 4-arg constructor `(status, failureReason, durationMs, phasesCompleted)` defaults `tokenUsage=0` and `question=null`

### Requirement: LoopPipeline interface

- MUST be a public interface in `org.specdriven.agent.loop`
- MUST define `execute(LoopCandidate candidate, LoopConfig config, Set<PipelinePhase> skipPhases)` as the primary method returning `IterationResult`
- When `skipPhases` is empty, behavior MUST be identical to the no-skipPhases invocation
- When `skipPhases` is non-empty, the implementation MUST skip those phases and start from the first non-skipped phase in `PipelinePhase.ordered()`
- `execute(LoopCandidate, LoopConfig)` MUST remain as a default method delegating to the new overload with an empty set
- Implementations MUST NOT throw checked exceptions — all failures MUST be captured in the returned `IterationResult` with appropriate `IterationStatus`

### Requirement: SpecDrivenPipeline

- MUST implement `LoopPipeline` in `org.specdriven.agent.loop`
- Constructor MUST accept a `Function<Path, LlmClient>` factory for creating LLM clients
- Constructor MUST accept a `Map<String, Tool>` tool registry
- Constructor MUST provide a convenience overload with default tools (bash, read, write, edit, glob, grep)
- `execute(candidate, config, skipPhases)` MUST iterate through `PipelinePhase.ordered()` sequentially, skipping any phases in `skipPhases`
- For each phase, MUST load the instruction template from classpath, build user prompt containing the selected candidate change name, milestone file, milestone goal, planned change summary, and project root, create Conversation, assemble SimpleAgentContext, run Orchestrator, track the phase as completed only if the orchestrator finishes without exception
- For each phase, MUST subscribe to `QUESTION_CREATED` events on the EventBus before running the orchestrator, and unsubscribe after the phase completes or is aborted
- When a `QUESTION_CREATED` event fires during a phase, MUST abort that phase and return `IterationResult(status=QUESTIONING, question=<captured question>, phasesCompleted=<phases completed before the interrupted phase>)`
- If any phase throws an exception (other than a QUESTION_CREATED abort), MUST stop execution and return an `IterationResult` with `status=FAILED` and `failureReason` describing which phase failed and why
- If timeout deadline is exceeded before a phase, MUST return `IterationResult` with `status=TIMED_OUT`
- If all phases complete, MUST return `IterationResult` with `status=SUCCESS`
- `durationMs` MUST reflect wall-clock time from start to finish of `execute()`

### Requirement: Phase instruction template resources

- MUST provide 6 text files under `src/main/resources/loop-phases/`: `recommend.txt`, `propose.txt`, `implement.txt`, `verify.txt`, `review.txt`, `archive.txt`
- Each template MUST be valid UTF-8 text
- Each template MUST describe: the phase objective, expected inputs, expected outputs, and tool usage guidance
- Templates MUST support variable substitution for `${changeName}`, `${milestoneGoal}`, `${plannedChangeSummary}`, `${projectRoot}`

### Requirement: StubLoopPipeline

- MUST be a package-private class in `org.specdriven.agent.loop`
- MUST implement `LoopPipeline`
- `execute()` MUST return `IterationResult` with `status=SUCCESS`, `failureReason=null`, `durationMs=0`, `phasesCompleted=empty`
- Exists solely for backward compatibility with the two-arg `DefaultLoopDriver` constructor

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
- Escalated partial iterations MUST NOT add the escalated change name to the recovered `completedChangeNames`
- Recovered progress MUST allow a previously escalated change to be selected again unless a later successful iteration completed it

### Requirement: Human escalation reason

- A human escalation reason MUST be non-empty
- For human-only categories, the reason MUST explain that the question category requires human approval
- For human delivery modes, the reason MUST explain that the configured delivery mode requires human handling
- For `LoopAnswerAgent` escalation responses, the reason MUST preserve the agent-provided escalation reason
- For absent `LoopAnswerAgent`, the reason MUST remain `no answer agent configured`

### Requirement: LOOP_PROGRESS_SAVED EventType

- MUST add `LOOP_PROGRESS_SAVED` to the existing `EventType` enum in `org.specdriven.agent.event`
- Existing EventType values MUST NOT change
- `DefaultLoopDriver` MUST publish this event after each successful `saveProgress()` call with metadata: `iterationCount` (int), `completedChangeCount` (int)

### Requirement: ContextBudget record

- MUST be a Java record in `org.specdriven.agent.loop` with fields: `maxTokens` (int), `warningThresholdPercent` (int)
- Compact constructor MUST reject `maxTokens` ≤ 0 with `IllegalArgumentException`
- `warningThresholdPercent` MUST be between 1 and 99 inclusive; compact constructor MUST reject out-of-range values with `IllegalArgumentException`
- Default `warningThresholdPercent` MUST be 20 (i.e., trigger exhaustion when less than 20% context remains)
- MUST provide static factory `of(int maxTokens)` returning a ContextBudget with default threshold
- MUST provide static factory `of(int maxTokens, int warningThresholdPercent)` returning a ContextBudget with specified threshold

### Requirement: LoopConfig contextBudget field

- `LoopConfig` MUST add an optional field `contextBudget` (ContextBudget, nullable)
- When `contextBudget` is null, no context tracking occurs — the loop behaves exactly as before
- Existing constructors and `defaults()` factory MUST remain backward-compatible, defaulting `contextBudget` to null
- A new static factory or constructor overload MUST accept `contextBudget`

### Requirement: IterationResult tokenUsage field

- `IterationResult` MUST add a field `tokenUsage` (long)
- `tokenUsage` MUST be non-negative; compact constructor MUST reject negative values with `IllegalArgumentException`
- Default value MUST be 0 for backward compatibility
- `StubLoopPipeline` MUST return `IterationResult` with `tokenUsage=0`

### Requirement: SpecDrivenPipeline token accumulation

- `SpecDrivenPipeline.execute()` MUST accumulate token usage from all `LlmResponse.usage()` values across all pipeline phases
- The returned `IterationResult.tokenUsage` MUST reflect the total tokens consumed during the iteration
- When no LLM calls are made, `tokenUsage` MUST be 0

### Requirement: LoopProgress tokenUsage field

- `LoopProgress` MUST add a field `tokenUsage` (long)
- `tokenUsage` MUST be non-negative; compact constructor MUST reject negative values with `IllegalArgumentException`
- Default value MUST be 0
- `tokenUsage` MUST represent the cumulative token usage for the persisted loop lineage, not only the most recent iteration or phase attempt

### Requirement: Context exhaustion detection in DefaultLoopDriver

- When `LoopConfig.contextBudget()` is non-null, `DefaultLoopDriver` MUST create a `ContextWindowManager` with the configured `maxTokens` on `start()`
- After each iteration completes, the driver MUST add the iteration's `IterationResult.tokenUsage()` to the `ContextWindowManager`
- Any persisted `LoopProgress.tokenUsage` snapshot used for context budgeting MUST equal the cumulative usage already applied to the `ContextWindowManager`, including previously recovered usage
- When an iteration pauses with `IterationStatus.QUESTIONING`, the saved `LoopProgress.tokenUsage` MUST still include the tokens consumed before the pause
- When an interrupted iteration resumes after question resolution, the saved `LoopProgress.tokenUsage` MUST include the tokens consumed before and after the resume point for that same logical iteration
- When `ContextWindowManager.remainingCapacity()` falls below `maxTokens * warningThresholdPercent / 100`, the driver MUST:
  1. Save progress via `store.saveProgress()` with current `tokenUsage` in the snapshot
  2. Publish `LOOP_CONTEXT_EXHAUSTED` event with metadata: `tokenUsage` (long), `maxTokens` (int), `remainingTokens` (long), `completedIterations` (int)
  3. Stop the loop with reason "context exhausted"
- When `LoopConfig.contextBudget()` is null, no context checking MUST occur

### Requirement: Context recovery on start

- When `DefaultLoopDriver.start()` recovers progress from the store and the recovered `LoopProgress.tokenUsage()` is greater than 0, the driver MUST initialize the `ContextWindowManager` with the recovered token usage value
- This ensures that consecutive sessions tracking the same context budget accumulate correctly
- Recovery MUST treat the persisted value as the loop's cumulative usage baseline and MUST NOT reinterpret it as only the last iteration's or last phase's token usage

### Requirement: LOOP_CONTEXT_EXHAUSTED EventType

- MUST add `LOOP_CONTEXT_EXHAUSTED` to the existing `EventType` enum in `org.specdriven.agent.event`
- Existing EventType values MUST NOT change

### Requirement: LealoneLoopIterationStore backward-compatible tokenUsage serialization

- `saveProgress()` MUST serialize the `tokenUsage` field in the JSON progress snapshot
- `loadProgress()` MUST deserialize `tokenUsage` from JSON when present, defaulting to 0 when the key is absent
- This ensures forward compatibility: old snapshots without `tokenUsage` can be loaded by new code

### Requirement: AnswerResolution sealed interface

- MUST be a sealed interface in `org.specdriven.agent.loop` with two permitted implementations:
  - `Resolved(Answer answer)` — record; `answer` MUST be non-null; indicates the LoopAnswerAgent successfully produced an answer
  - `Escalated(String reason)` — record; `reason` MUST be non-null; indicates the question cannot be automatically resolved
- MUST NOT expose any mutable state

### Requirement: LoopAnswerAgent interface

- MUST be a public interface in `org.specdriven.agent.loop`
- MUST define `resolve(Question question, int timeoutSeconds)` returning `AnswerResolution`
- MUST NOT throw checked exceptions — all failures MUST be captured in the returned `AnswerResolution`
- When `timeoutSeconds` is exceeded, MUST return `Escalated("timeout")`
- Implementations MUST NOT modify the Question object

### Requirement: DefaultLoopAnswerAgent

- MUST implement `LoopAnswerAgent` in `org.specdriven.agent.loop`
- Constructor MUST accept `LlmClient llmClient` and `QuestionRuntime questionRuntime`; both MUST be non-null
- `resolve()` MUST construct a prompt from `question.question()`, `question.impact()`, and `question.recommendation()`
- MUST run a single-turn LLM call (no tools) with the prompt within the specified timeout using a virtual thread executor
- MUST parse the LLM response and construct an `Answer` with `source=AI_AGENT`, `decision=ANSWER_ACCEPTED`, `deliveryMode=AUTO_AI_REPLY`, `confidence=0.8`, `basisSummary="AI agent single-turn analysis"`, `sourceRef="LoopAnswerAgent"`, `answeredAt=System.currentTimeMillis()`
- MUST call `questionRuntime.submitAnswer(question.sessionId(), question.questionId(), answer)` after constructing the Answer
- When the LLM call or `submitAnswer` throws, MUST return `Escalated("<exception message>")`
- When timeout is exceeded, MUST interrupt the virtual thread and return `Escalated("timeout")`

## MODIFIED Requirements

### Requirement: SpecDrivenPipeline smart context integration
`SpecDrivenPipeline` MUST integrate smart context optimization into autonomous loop phase execution when context budgeting is configured.

#### Scenario: Context-budgeted pipeline optimizes LLM messages
- GIVEN a `LoopConfig` with a non-null context budget
- AND a `SpecDrivenPipeline` executing a phase with an LLM client factory
- WHEN the pipeline passes the phase client to `DefaultOrchestrator`
- THEN LLM calls made by the phase MUST use smart context optimization before reaching the underlying provider client

#### Scenario: Pipeline without context budget remains unchanged
- GIVEN a `LoopConfig` with no context budget
- AND a `SpecDrivenPipeline` executing a phase with an LLM client factory
- WHEN the pipeline passes the phase client to `DefaultOrchestrator`
- THEN LLM calls made by the phase MUST reach the underlying provider client with the same message contents as before this change

#### Scenario: Loop token usage still reflects provider responses
- GIVEN a context-budgeted pipeline using smart context optimization
- AND provider responses include token usage
- WHEN pipeline execution completes
- THEN `IterationResult.tokenUsage()` MUST still reflect the token usage reported by provider responses
- AND context-exhaustion behavior MUST continue to use that reported usage

### Requirement: DefaultOrchestrator signature compatibility
Smart context integration MUST NOT require a new public `DefaultOrchestrator.run(...)` signature.

#### Scenario: Existing orchestrator callers still compile
- GIVEN existing code that calls `DefaultOrchestrator.run(AgentContext, LlmClient)`
- WHEN this change is applied
- THEN the call MUST remain valid

#### Scenario: Wrapped client enables optimization
- GIVEN `DefaultOrchestrator.run(AgentContext, LlmClient)` receives a smart-context-wrapped client
- WHEN the orchestrator makes an LLM call
- THEN optimization MUST occur through the supplied `LlmClient`
- AND orchestrator state transitions, tool execution, question handling, and conversation append behavior MUST remain governed by existing orchestrator requirements

## ADDED Requirements — interactive-session-interface

### Requirement: Interactive session boundary for later human-in-loop integration

- The autonomous loop integration surface MUST treat interactive human handling as a separate session contract rather than as direct `DefaultLoopDriver`-specific methods
- The first M29 contract change MUST define this boundary without requiring `DefaultLoopDriver` to enter interactive mode yet
- Later interactive bridge work MAY depend on `InteractiveSession`, but this change MUST NOT require any new `LoopDriver` public methods

#### Scenario: Existing loop public API remains unchanged

- GIVEN code that already integrates with `LoopDriver`
- WHEN the interactive session contract is introduced
- THEN existing `LoopDriver` start, pause, resume, stop, and iteration query methods MUST remain sufficient to compile unchanged callers

#### Scenario: Interactive contract can be layered onto paused loop handling later

- GIVEN a loop iteration paused because of a human-escalated question
- WHEN a later roadmap change bridges loop escalation into interactive mode
- THEN that bridge MUST be able to depend on `InteractiveSession` as the session boundary
- AND this change MUST NOT predefine the later bridge's runtime behavior beyond that contract dependency

### Requirement: InteractiveSessionFactory interface

- MUST be a functional interface in `org.specdriven.agent.loop`
- MUST define `InteractiveSession create(String sessionId)`
- `sessionId` MUST be non-null and non-blank
- `create()` MUST return a non-null `InteractiveSession` in `NEW` state
- `create()` MUST NOT throw checked exceptions — factory failures MUST be captured in the returned session (which enters `ERROR` state on `start()`)

### Requirement: DefaultLoopDriver interactive session bridge

- The DefaultLoopDriver MUST accept an additional optional constructor parameter `InteractiveSessionFactory` (nullable).
- When `InteractiveSessionFactory` is null, human-escalated pause behavior MUST remain identical to current behavior — no interactive session is created.
- When `InteractiveSessionFactory` is non-null and the loop transitions to `PAUSED` due to a human-escalated question:
  1. MUST call `factory.create(sessionId)` where `sessionId` is the current loop session identifier
  2. MUST call `start()` on the created `InteractiveSession`
  3. MUST publish `LOOP_INTERACTIVE_ENTERED` event with metadata: `sessionId` (String), `questionId` (String), `changeName` (String)
  4. MUST block the scheduling thread while the interactive session state is `ACTIVE`
  5. MUST periodically check whether the session has transitioned to `CLOSED` or `ERROR`
  6. When the session transitions to `CLOSED` or `ERROR`, MUST publish `LOOP_INTERACTIVE_EXITED` event with metadata: `sessionId` (String), `questionId` (String), `sessionEndState` (String — "CLOSED" or "ERROR")
  7. MUST NOT add the paused change name to `completedChangeNames` during the interactive session
  8. After session exit, the loop MUST remain in `PAUSED` state; resume MUST be triggered externally (via `resume()` call or stop via `stop()` call)
- The scheduling thread MUST react to `stop()` being called while an interactive session is active: MUST close the interactive session and exit the scheduling loop.

### Requirement: LOOP_INTERACTIVE_ENTERED EventType

- MUST add `LOOP_INTERACTIVE_ENTERED` to the existing `EventType` enum in `org.specdriven.agent.event`
- MUST include metadata: `sessionId` (String), `questionId` (String), `changeName` (String)
- Existing EventType values MUST NOT change

### Requirement: LOOP_INTERACTIVE_EXITED EventType

- MUST add `LOOP_INTERACTIVE_EXITED` to the existing `EventType` enum in `org.specdriven.agent.event`
- MUST include metadata: `sessionId` (String), `questionId` (String), `sessionEndState` (String — "CLOSED" or "ERROR")
- Existing EventType values MUST NOT change

### Requirement: Interactive session factory backward compatibility

- All existing `DefaultLoopDriver` constructors MUST remain valid and MUST behave as if `InteractiveSessionFactory` is null
- A new constructor overload MUST accept the additional `InteractiveSessionFactory` parameter
- Code that does not pass `InteractiveSessionFactory` MUST compile and run unchanged

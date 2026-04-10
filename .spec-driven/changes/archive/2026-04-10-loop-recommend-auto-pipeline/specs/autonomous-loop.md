# Autonomous Loop Driver (Delta)

## ADDED Requirements

### Requirement: PipelinePhase enum

- MUST be a public enum in `org.specdriven.agent.loop` with values: PROPOSE, IMPLEMENT, VERIFY, REVIEW, ARCHIVE
- Each value MUST provide a `templateResource()` method returning a `String` classpath path to its instruction template file
- Template resource paths MUST follow the pattern `/loop-phases/<lowercase-name>.txt`
- MUST implement `Iterable<PipelinePhase>` via a static `ordered()` method returning phases in execution order (PROPOSE → IMPLEMENT → VERIFY → REVIEW → ARCHIVE)

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
- Constructor MUST provide a convenience overload with default tools (bash, read, write, edit, glob, grep) matching `SkillServiceExecutor.DEFAULT_TOOLS`
- `execute()` MUST iterate through `PipelinePhase.ordered()` sequentially
- For each phase, MUST:
  - Load the instruction template from classpath using `PipelinePhase.templateResource()`
  - Build a user prompt containing the candidate change name, milestone goal, and project root path
  - Create a `Conversation` with a system message (template content) and user message (constructed prompt)
  - Assemble a `SimpleAgentContext` with the tool registry, conversation, and working directory set to `config.projectRoot()`
  - Run an `Orchestrator` with the default `OrchestratorConfig`
  - Track the phase as completed only if the orchestrator finishes without exception
- If any phase throws an exception or is interrupted by timeout, MUST stop execution and return an `IterationResult` with `status=FAILED` or `status=TIMED_OUT` and `failureReason` describing which phase failed and why
- If all phases complete, MUST return `IterationResult` with `status=SUCCESS`
- `durationMs` MUST reflect wall-clock time from start to finish of `execute()`

### Requirement: Phase instruction template resources

- MUST provide 5 text files under `src/main/resources/loop-phases/`: `propose.txt`, `implement.txt`, `verify.txt`, `review.txt`, `archive.txt`
- Each template MUST be valid UTF-8 text
- Each template MUST describe: the phase objective, expected inputs (file paths), expected outputs (file paths), and tool usage guidance
- Templates MUST support variable substitution for `${changeName}`, `${milestoneGoal}`, `${projectRoot}`

### Requirement: DefaultLoopDriver pipeline integration

- `DefaultLoopDriver` constructor MUST accept an additional `LoopPipeline` parameter
- A backward-compatible constructor `DefaultLoopDriver(LoopConfig, LoopScheduler)` MUST remain, using a `StubLoopPipeline` that returns `IterationResult` with `status=SUCCESS` and empty phases
- `runLoop()` MUST call `pipeline.execute(candidate, config)` instead of the current hardcoded SUCCESS stub
- The completed `LoopIteration` MUST use the `IterationResult.status` for its `status` field
- The completed `LoopIteration` MUST use the `IterationResult.failureReason` for its `failureReason` field when status is not SUCCESS
- `completedAt` MUST be set to the current time after `pipeline.execute()` returns, not before

### Requirement: StubLoopPipeline

- MUST be a package-private class in `org.specdriven.agent.loop`
- MUST implement `LoopPipeline`
- `execute()` MUST return `IterationResult` with `status=SUCCESS`, `failureReason=null`, `durationMs=0`, `phasesCompleted=empty`
- Exists solely for backward compatibility with the two-arg `DefaultLoopDriver` constructor

## MODIFIED Requirements

### Requirement: DefaultLoopDriver (from autonomous-loop.md)

- `start()` MUST launch a VirtualThread running the scheduling loop — UNCHANGED
- The scheduling loop MUST check `maxIterations` before each iteration — UNCHANGED
- The scheduling loop MUST call `pipeline.execute(candidate, config)` during the RUNNING state — MODIFIED (was: immediately mark SUCCESS)
- State transitions and event publication behavior — UNCHANGED
- Thread safety and synchronized block usage — UNCHANGED

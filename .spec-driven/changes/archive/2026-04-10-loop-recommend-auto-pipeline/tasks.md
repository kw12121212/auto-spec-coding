# Tasks: loop-recommend-auto-pipeline

## Implementation

- [x] Add `PipelinePhase` enum in `org.specdriven.agent.loop` with values: PROPOSE, IMPLEMENT, VERIFY, REVIEW, ARCHIVE — each with a `templateResource()` method returning the classpath path to its instruction template
- [x] Add `IterationResult` record in `org.specdriven.agent.loop` with fields: `status` (IterationStatus), `failureReason` (String, nullable), `durationMs` (long), `phasesCompleted` (List<PipelinePhase>) — immutable, compact constructor defensively copies phasesCompleted
- [x] Add `LoopPipeline` interface in `org.specdriven.agent.loop` — single method `execute(LoopCandidate candidate, LoopConfig config)` returning `IterationResult`
- [x] Add 5 phase instruction template resources under `src/main/resources/loop-phases/`: `propose.txt`, `implement.txt`, `verify.txt`, `review.txt`, `archive.txt` — each defining the phase goal, expected inputs, expected outputs, and tool usage guidance
- [x] Add `SpecDrivenPipeline` in `org.specdriven.agent.loop` implementing `LoopPipeline` — iterates through PipelinePhase values, for each phase loads template, builds Conversation with system+user messages, creates SimpleAgentContext with standard tools, runs Orchestrator, validates phase output (file existence for propose/verify, non-empty LLM response for others)
- [x] Modify `DefaultLoopDriver` — add `LoopPipeline` constructor parameter, replace the stub execution in `runLoop()` with `pipeline.execute(candidate, config)`, use IterationResult.status to set IterationStatus and failureReason on the completed iteration
- [x] Add backward-compatible `DefaultLoopDriver(LoopConfig, LoopScheduler)` constructor that uses a no-op `StubLoopPipeline` for existing tests

## Testing

- [x] Run `mvn compile -q` — lint/compile validation
- [x] Run `mvn test -pl . -Dtest="PipelinePhaseTest,IterationResultTest,SpecDrivenPipelineTest,DefaultLoopDriverTest"` — unit tests covering phase enum, result record, pipeline execution with mock LLM, and driver integration

## Verification

- [x] Verify `LoopPipeline` interface is pluggable — `DefaultLoopDriver` works with any `LoopPipeline` implementation
- [x] Verify `SpecDrivenPipeline` loads each phase template from classpath and builds correct Conversation
- [x] Verify `DefaultLoopDriver.runLoop()` uses `LoopPipeline.execute()` result instead of hardcoded SUCCESS
- [x] Verify error classification: FAILED on output validation failure, TIMED_OUT on timeout, SUCCESS on normal completion
- [x] Verify existing `DefaultLoopDriverTest` and `SequentialMilestoneSchedulerTest` still pass unchanged

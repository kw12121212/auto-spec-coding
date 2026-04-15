# Design: loop-skill-phase-runner

## Approach

Refactor `SpecDrivenPipeline` around a new `SpecDrivenPhaseRunner` interface. The pipeline remains responsible for iteration ordering, skip-phase handling, question-event interception, timeout checks before each phase, and aggregate `IterationResult` construction. The phase runner becomes responsible for executing one phase and returning a structured `PhaseExecutionResult`.

Add `CommandSpecDrivenPhaseRunner` as the real workflow execution adapter. It runs configured commands in `LoopConfig.projectRoot()`, substitutes loop candidate variables into command arguments, applies the loop iteration timeout to each command invocation, and maps process exit status to phase success or failure. The default command map treats `RECOMMEND` as already satisfied by loop candidate selection and maps the remaining phases to `spec-driven <subcommand> <change-name>`.

Move the existing prompt-template behavior into an internal prompt-backed runner used by the existing LLM constructors. This keeps current tests and callers stable while making command-backed execution available through the explicit runner constructor.

## Key Decisions

- Keep `SpecDrivenPipeline` as the orchestration boundary rather than moving ordering logic into each runner. This keeps phase order and skip semantics consistent for all runner implementations.
- Represent phase outcomes with `PhaseExecutionResult` instead of throwing checked exceptions. This matches `LoopPipeline` behavior, where all execution failures are surfaced through `IterationResult`.
- Treat `RECOMMEND` as a no-command success in the default command runner because the loop scheduler already selected and validated the roadmap candidate before pipeline execution.
- Preserve the old prompt-backed constructors to avoid breaking existing integrations and tests while allowing command-backed execution through `new SpecDrivenPipeline(SpecDrivenPhaseRunner)`.
- Keep command templates configurable so deployments can bind phases to Java CLI commands, shell wrappers, or installed spec-driven skill launchers without changing loop code.

## Alternatives Considered

- Replace all existing constructors with command-backed execution. This would be a breaking API change and would remove the current testable in-process prompt behavior.
- Keep embedding command execution directly in `SpecDrivenPipeline`. This would make the pipeline harder to test and would mix phase ordering with command-launch mechanics.
- Implement phase checkpoint recovery in this change. That is a separate M35 planned item and would expand the scope beyond the phase runner boundary.

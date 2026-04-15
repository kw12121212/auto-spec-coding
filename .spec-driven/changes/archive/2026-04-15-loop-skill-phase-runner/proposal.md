# loop-skill-phase-runner

## What

Introduce an explicit phase runner boundary for `SpecDrivenPipeline` so autonomous loop phases can be executed by real spec-driven workflow commands instead of being tied only to embedded LLM prompt templates.

The change adds a command-backed phase runner for `propose`, `apply`, `verify`, `review`, and `archive` phases, while keeping the existing LLM-template constructors compatible for callers that still instantiate the prompt-backed pipeline directly.

## Why

M35 requires the autonomous loop to move from implicit phase prompts toward an explicit `roadmap-recommend -> spec-driven-propose -> spec-driven-apply -> spec-driven-verify -> spec-driven-review -> spec-driven-archive` pipeline. The current `SpecDrivenPipeline` embeds prompt execution directly in the pipeline class, which makes it hard to swap in real workflow execution and hard to audit phase-level results.

This change establishes the runner contract and command-backed implementation needed before later M35 work can add phase session reset, checkpoint recovery, and cumulative context-budget semantics.

## Scope

- Add a phase execution result contract for individual loop phases.
- Add a phase runner interface used by `SpecDrivenPipeline`.
- Add a command-backed phase runner that executes configured spec-driven workflow commands from the project root and reports success, failure, or timeout.
- Refactor `SpecDrivenPipeline` so it delegates each non-skipped phase to its configured runner and records completed phases only after successful phase execution.
- Preserve question interruption behavior for in-process prompt-backed runners.
- Preserve existing `SpecDrivenPipeline` constructors and their current prompt-backed behavior for compatibility.
- Add focused unit tests for runner delegation, failure handling, command execution, and backward-compatible prompt behavior.

Out of scope:

- Phase-level checkpoint persistence and resume semantics.
- Fresh session/context reset guarantees between phases.
- Changing manual `/roadmap-recommend` confirmation behavior.
- Rewriting the spec-driven skill workflows themselves.
- Adding a new UI or interactive human-in-loop entry point.

## Unchanged Behavior

- `PipelinePhase.ordered()` remains `RECOMMEND -> PROPOSE -> IMPLEMENT -> VERIFY -> REVIEW -> ARCHIVE`.
- Existing `SpecDrivenPipeline(Function<Path, LlmClient>)` and `SpecDrivenPipeline(Function<Path, LlmClient>, Map<String, Tool>)` callers continue to compile and use the existing prompt-backed phase execution path.
- `QUESTION_CREATED` events raised during in-process phase execution still interrupt the current phase and return `IterationStatus.QUESTIONING`.
- Skipped phases are not executed and are not reported as newly completed.
- Token usage reported by prompt-backed LLM execution continues to be accumulated into the returned `IterationResult`.

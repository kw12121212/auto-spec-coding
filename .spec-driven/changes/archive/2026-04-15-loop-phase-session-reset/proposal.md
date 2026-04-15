# loop-phase-session-reset

## What

Define and enforce the autonomous loop phase session reset contract for the
`roadmap-recommend -> spec-driven-propose -> spec-driven-apply -> spec-driven-verify -> spec-driven-review -> spec-driven-archive`
pipeline.

Each loop phase will start from a fresh execution context. A phase may read the
selected roadmap candidate, project files, spec-driven artifacts, repository
state, and persisted loop state, but it must not inherit the previous phase's
chat history, `Conversation`, `AgentContext` session, or command process state as
authoritative input.

## Why

M35 has already introduced a first-class recommend phase and a command-backed
spec-driven phase runner. The next risk is boundary drift: if later phases can
silently depend on earlier phase chat history, the loop remains vulnerable to
long-context contamination and cannot safely support phase-level checkpoint
recovery.

This change makes the phase boundary observable and testable before adding
checkpoint recovery or cumulative context-budget refinements.

## Scope

In scope:

- Define the reset contract for all autonomous loop phases.
- Ensure prompt-backed phase execution creates a fresh phase session and
  conversation for each phase.
- Ensure command-backed phase execution launches each phase independently and
  does not reuse command process state between phases.
- Preserve the selected `LoopCandidate` across phases while treating files and
  persisted state as the cross-phase handoff mechanism.
- Add focused tests covering fresh phase context, command process isolation, and
  question/resume behavior at phase boundaries.

Out of scope:

- Phase-level checkpoint persistence and resume storage.
- New recovery behavior for interrupted phases beyond the existing
  `phasesCompleted` skip contract.
- Changes to manual `/roadmap-recommend` confirmation behavior.
- Rewriting the spec-driven skill workflows themselves.
- Adding a new UI, SQL/NL interaction surface, or human-in-loop behavior.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- `PipelinePhase.ordered()` remains `RECOMMEND -> PROPOSE -> IMPLEMENT -> VERIFY -> REVIEW -> ARCHIVE`.
- The selected loop candidate remains stable for all phases in one iteration.
- Prompt-backed pipeline constructors continue to work for existing callers.
- Command-backed phase runner command templates and substitutions remain
  compatible.
- Token usage accumulation remains iteration-level and still sums provider
  responses across phases.
- Manual roadmap recommendation still requires explicit user confirmation before
  proposal artifacts are scaffolded.

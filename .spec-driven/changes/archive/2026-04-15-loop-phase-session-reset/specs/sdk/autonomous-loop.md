---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/loop/CommandSpecDrivenPhaseRunner.java
    - src/main/java/org/specdriven/agent/loop/SpecDrivenPipeline.java
  tests:
    - src/test/java/org/specdriven/agent/loop/SpecDrivenPipelineTest.java
---

# Autonomous Loop Driver (Delta)

## ADDED Requirements

### Requirement: Loop phase session reset

The autonomous loop pipeline MUST start each non-skipped phase with a fresh phase
execution context.

#### Scenario: prompt-backed phases do not inherit chat history
- GIVEN a prompt-backed `SpecDrivenPipeline` executing multiple phases for one loop candidate
- WHEN a later phase begins
- THEN the later phase MUST receive a new phase session
- AND it MUST NOT receive the previous phase's `Conversation` history as input
- AND it MUST use phase-local LLM client state for that phase's calls
- AND it MUST still receive the selected candidate name, milestone file, milestone goal, planned change summary, and project root

#### Scenario: command-backed phases do not reuse process state
- GIVEN a command-backed phase runner executes two command phases in one iteration
- WHEN the second command phase starts
- THEN it MUST run as an independent command invocation in the configured project root
- AND it MUST NOT reuse the first phase's process, stdin stream, stdout stream, or stderr stream as phase input
- AND command template substitution MUST still receive the selected candidate and project variables

#### Scenario: files remain the cross-phase handoff
- GIVEN an earlier phase creates or modifies spec-driven artifacts or repository files
- WHEN a later phase starts with fresh phase context
- THEN the later phase MAY observe those artifacts and files from disk
- AND the later phase MUST treat those artifacts, repository state, and persisted loop state as the authoritative cross-phase handoff

#### Scenario: skipped phases do not leak prior context
- GIVEN a pipeline resumes with one or more phases listed in `skipPhases`
- WHEN execution continues at the first non-skipped phase
- THEN that phase MUST start with a fresh phase execution context
- AND it MUST NOT depend on the interrupted phase's chat history or session object

### Requirement: Phase session identity

Each prompt-backed phase execution MUST expose a phase-local session identity to
components that observe the `AgentContext`.

#### Scenario: phase sessions are distinct
- GIVEN two prompt-backed phases execute during one loop iteration
- WHEN each phase creates its agent execution context
- THEN each phase MUST use a distinct non-blank session identifier
- AND the session identifier MUST remain stable within that single phase execution
- AND the LLM client factory MUST be invoked separately for each executed prompt-backed phase

#### Scenario: question events identify the interrupted phase session
- GIVEN a prompt-backed phase raises a structured question
- WHEN the pipeline returns `IterationResult(status=QUESTIONING)`
- THEN the returned question MUST identify the session for the interrupted phase
- AND `phasesCompleted` MUST include only phases completed before that interrupted phase

## MODIFIED Requirements

### Requirement: SpecDrivenPipeline

Previously: `SpecDrivenPipeline` delegated phases in order but did not explicitly
state the phase context isolation contract.

`SpecDrivenPipeline` MUST preserve phase ordering while enforcing fresh phase
execution context for every non-skipped phase.

#### Scenario: successful phase completion still reports ordered phases
- GIVEN all phases complete successfully
- WHEN `SpecDrivenPipeline.execute()` returns
- THEN `phasesCompleted` MUST still report phases in `PipelinePhase.ordered()` order
- AND token usage MUST still represent the total tokens consumed across the iteration

#### Scenario: selected candidate is stable across reset boundaries
- GIVEN a loop candidate selected by the recommend phase
- WHEN later phases execute with fresh phase context
- THEN every phase MUST operate on the same selected change name and milestone file
- AND no phase MAY reselect a different roadmap candidate for the same iteration

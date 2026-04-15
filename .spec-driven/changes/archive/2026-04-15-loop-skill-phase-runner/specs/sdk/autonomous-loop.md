---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/loop/CommandSpecDrivenPhaseRunner.java
    - src/main/java/org/specdriven/agent/loop/PhaseExecutionResult.java
    - src/main/java/org/specdriven/agent/loop/SpecDrivenPhaseRunner.java
    - src/main/java/org/specdriven/agent/loop/SpecDrivenPipeline.java
  tests:
    - src/test/java/org/specdriven/agent/loop/SpecDrivenPipelineTest.java
---

# Autonomous Loop Driver (Delta)

## ADDED Requirements

### Requirement: Spec-driven phase runner contract

The autonomous loop pipeline MUST expose a single-phase execution contract for spec-driven workflow phases.

#### Scenario: phase runner executes one phase
- GIVEN a loop candidate and loop configuration
- WHEN a phase runner is asked to run a `PipelinePhase`
- THEN it MUST return a structured phase result describing success, failure, timeout, token usage, and any captured question

#### Scenario: phase result validates question state
- GIVEN a phase result with `status=QUESTIONING`
- THEN it MUST contain a non-null question
- AND phase results with any other status MUST NOT contain a question

### Requirement: Command-backed spec-driven phase runner

The autonomous loop MUST provide a command-backed phase runner for real spec-driven workflow command execution.

#### Scenario: default phase command mapping
- GIVEN the default command-backed phase runner
- WHEN a non-recommend phase is executed for change `demo-change`
- THEN it MUST run a command for the corresponding spec-driven workflow subcommand with `demo-change` as the change name

#### Scenario: recommend phase needs no command
- GIVEN the default command-backed phase runner
- WHEN the `RECOMMEND` phase is executed
- THEN it MUST succeed without launching an external command
- AND the already selected loop candidate MUST remain unchanged for later phases

#### Scenario: command failure is reported
- GIVEN a configured phase command exits with a non-zero status
- WHEN the command-backed runner executes that phase
- THEN the phase result MUST report `FAILED`
- AND the failure reason MUST include the phase name and exit code

#### Scenario: command timeout is reported
- GIVEN a configured phase command does not finish before the loop iteration timeout
- WHEN the command-backed runner executes that phase
- THEN the phase result MUST report `TIMED_OUT`
- AND the process MUST be destroyed

## MODIFIED Requirements

### Requirement: SpecDrivenPipeline

Previously: `SpecDrivenPipeline` directly loaded phase templates, constructed an agent context, and ran the orchestrator for each phase.

`SpecDrivenPipeline` MUST delegate each non-skipped phase to a configured `SpecDrivenPhaseRunner`.

#### Scenario: phases are delegated in order
- GIVEN a `SpecDrivenPipeline` configured with a phase runner
- WHEN the pipeline executes a candidate with no skipped phases
- THEN it MUST call the runner for phases in `PipelinePhase.ordered()` order
- AND it MUST report each successfully executed phase as completed

#### Scenario: skipped phases are not delegated
- GIVEN a `SpecDrivenPipeline` configured with a phase runner
- AND `skipPhases` contains `PROPOSE`
- WHEN the pipeline executes a candidate
- THEN it MUST NOT call the runner for `PROPOSE`
- AND it MUST continue delegating the remaining non-skipped phases in order

#### Scenario: failed phase stops the iteration
- GIVEN a phase runner returns `FAILED` for a phase
- WHEN the pipeline executes that phase
- THEN the pipeline MUST stop without delegating later phases
- AND it MUST return an `IterationResult` with `status=FAILED`
- AND `phasesCompleted` MUST contain only phases completed before the failed phase

#### Scenario: prompt-backed constructor compatibility
- GIVEN existing code constructs `SpecDrivenPipeline` with an LLM client factory
- WHEN the pipeline executes a candidate
- THEN the existing prompt-backed phase behavior MUST remain available
- AND token usage from prompt-backed LLM responses MUST continue to be accumulated in the returned iteration result

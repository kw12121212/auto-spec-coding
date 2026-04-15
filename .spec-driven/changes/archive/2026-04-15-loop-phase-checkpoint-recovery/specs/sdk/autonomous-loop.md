---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/loop/DefaultLoopDriver.java
    - src/main/java/org/specdriven/agent/loop/LealoneLoopIterationStore.java
    - src/main/java/org/specdriven/agent/loop/LoopIterationStore.java
    - src/main/java/org/specdriven/agent/loop/LoopPhaseCheckpoint.java
    - src/main/java/org/specdriven/agent/loop/LoopProgress.java
    - src/main/java/org/specdriven/agent/loop/SpecDrivenPipeline.java
  tests:
    - src/test/java/org/specdriven/agent/loop/DefaultLoopDriverTest.java
    - src/test/java/org/specdriven/agent/loop/LealoneLoopIterationStoreTest.java
    - src/test/java/org/specdriven/agent/loop/LoopProgressTest.java
    - src/test/java/org/specdriven/agent/loop/SpecDrivenPipelineTest.java
---

# Autonomous Loop Driver (Delta)

## ADDED Requirements

### Requirement: Loop phase checkpoint

The autonomous loop MUST persist an active phase checkpoint whenever one
iteration has selected a roadmap candidate but has not yet completed all phases
successfully.

#### Scenario: checkpoint records selected candidate
- GIVEN the loop has selected a roadmap candidate for an iteration
- WHEN progress is persisted before the iteration completes successfully
- THEN the persisted progress MUST identify the selected change name
- AND it MUST identify the selected milestone file
- AND it MUST identify the selected milestone goal and planned change summary when those values are available

#### Scenario: checkpoint records completed phases
- GIVEN one or more pipeline phases complete successfully for the selected candidate
- WHEN progress is persisted before the iteration completes successfully
- THEN the persisted progress MUST include exactly the phases that completed successfully
- AND it MUST preserve the phase order defined by `PipelinePhase.ordered()`
- AND it MUST NOT include the interrupted, failed, timed-out, or not-yet-started phase

#### Scenario: completed iteration clears active checkpoint
- GIVEN a checkpointed iteration later completes successfully
- WHEN the loop persists the completed iteration progress
- THEN the active phase checkpoint MUST be absent from the saved progress
- AND the completed change name MUST be present in `completedChangeNames`

### Requirement: Phase checkpoint recovery

The autonomous loop MUST recover an active phase checkpoint before selecting a
new roadmap candidate.

#### Scenario: startup resumes checkpointed candidate
- GIVEN persisted progress contains an active phase checkpoint
- WHEN the loop starts
- THEN the next pipeline execution MUST use the checkpointed change name and milestone file
- AND the scheduler MUST NOT select a different candidate before the checkpointed iteration is resumed

#### Scenario: completed phases are skipped during recovery
- GIVEN persisted progress contains an active phase checkpoint with completed phases
- WHEN the loop resumes the checkpointed candidate
- THEN the pipeline MUST receive the completed phases as skipped phases
- AND execution MUST continue at the first incomplete phase
- AND the resumed phase MUST still start with a fresh phase execution context

#### Scenario: incomplete phase is retried during recovery
- GIVEN a phase was interrupted by a question, failure, timeout, pause, or stop
- WHEN the loop resumes from the persisted checkpoint
- THEN the interrupted phase MUST NOT be treated as completed
- AND that phase MUST be eligible to run again

#### Scenario: no checkpoint keeps existing scheduling behavior
- GIVEN persisted progress has no active phase checkpoint
- WHEN the loop starts or resumes
- THEN the loop MUST use the scheduler to select the next eligible roadmap candidate as before

### Requirement: Human escalation checkpoint recovery

Human-escalated questions MUST preserve the active phase checkpoint without
marking the change complete.

#### Scenario: human escalation saves retryable checkpoint
- GIVEN a phase raises a question that requires human handling
- WHEN the loop pauses for human escalation
- THEN persisted progress MUST keep the selected candidate checkpoint
- AND persisted progress MUST include only phases completed before the interrupted phase
- AND persisted progress MUST NOT include the paused change name in `completedChangeNames`

#### Scenario: resume after human escalation continues checkpointed work
- GIVEN the loop is resumed after a human-escalated pause
- WHEN an active phase checkpoint exists for the paused change
- THEN the loop MUST retry the paused change before selecting any other roadmap candidate
- AND the retry MUST skip only phases completed before the escalation

### Requirement: Phase checkpoint persistence compatibility

Loop progress persistence MUST remain backward-compatible with progress snapshots
that do not contain phase checkpoint data.

#### Scenario: old progress snapshot loads without checkpoint
- GIVEN stored loop progress was written before phase checkpoint fields existed
- WHEN progress is loaded
- THEN the result MUST be treated as having no active phase checkpoint
- AND existing fields such as loop state, completed change names, total iterations, and token usage MUST still load normally

#### Scenario: checkpoint round trip preserves phase data
- GIVEN loop progress contains an active phase checkpoint
- WHEN the progress is saved and loaded
- THEN the loaded progress MUST preserve the selected candidate fields
- AND it MUST preserve the completed phase set in phase order

## MODIFIED Requirements

### Requirement: LoopProgress record

Previously: `LoopProgress` represented loop state, completed change names, total
iterations, and cumulative token usage.

`LoopProgress` MUST also expose an optional active phase checkpoint for the
currently selected but incomplete loop iteration.

#### Scenario: progress without active work has no checkpoint
- GIVEN no selected iteration is currently incomplete
- WHEN `LoopProgress` is inspected
- THEN its active phase checkpoint MUST be absent

#### Scenario: progress defensively copies checkpoint phase data
- GIVEN progress is created with active checkpoint phase data
- WHEN the caller later mutates the original phase collection
- THEN the `LoopProgress` checkpoint phase data MUST remain unchanged

### Requirement: DefaultLoopDriver persistence integration

Previously: `DefaultLoopDriver` recovered completed change names and iteration
history, then used the scheduler to select the next candidate.

`DefaultLoopDriver` MUST recover an active phase checkpoint first and resume that
candidate before asking the scheduler for a new candidate.

#### Scenario: failed or timed-out phase does not complete change
- GIVEN a pipeline phase returns `FAILED` or `TIMED_OUT`
- WHEN the loop persists progress for that iteration
- THEN the selected candidate checkpoint MUST remain available for retry
- AND the selected change name MUST NOT be added to `completedChangeNames`

#### Scenario: successful recovery completes original candidate
- GIVEN a loop resumes from an active phase checkpoint
- AND all remaining phases complete successfully
- WHEN the iteration is persisted
- THEN the completed iteration MUST use the checkpointed change name and milestone file
- AND the active phase checkpoint MUST be cleared

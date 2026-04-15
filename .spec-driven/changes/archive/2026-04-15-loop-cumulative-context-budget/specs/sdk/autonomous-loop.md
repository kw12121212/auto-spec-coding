---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/loop/ContextBudget.java
    - src/main/java/org/specdriven/agent/loop/DefaultLoopDriver.java
    - src/main/java/org/specdriven/agent/loop/IterationResult.java
    - src/main/java/org/specdriven/agent/loop/LealoneLoopIterationStore.java
    - src/main/java/org/specdriven/agent/loop/LoopPhaseCheckpoint.java
    - src/main/java/org/specdriven/agent/loop/LoopProgress.java
    - src/main/java/org/specdriven/agent/loop/SpecDrivenPipeline.java
    - src/main/java/org/specdriven/agent/loop/TokenAccumulator.java
  tests:
    - src/test/java/org/specdriven/agent/loop/ContextBudgetTest.java
    - src/test/java/org/specdriven/agent/loop/DefaultLoopDriverTest.java
    - src/test/java/org/specdriven/agent/loop/LealoneLoopIterationStoreTest.java
    - src/test/java/org/specdriven/agent/loop/LoopProgressTest.java
    - src/test/java/org/specdriven/agent/loop/SpecDrivenPipelineTest.java
---

## MODIFIED Requirements

### Requirement: LoopProgress tokenUsage field

Previously: `LoopProgress.tokenUsage` represented cumulative token usage for
the persisted loop lineage.

`LoopProgress.tokenUsage` MUST represent the cumulative token usage for the
persisted loop lineage across all phase attempts, including attempts that fail,
time out, ask a question, pause for human handling, or are later retried.

#### Scenario: interrupted phase attempt contributes to cumulative usage
- GIVEN a selected roadmap change is running with context budgeting enabled
- AND a phase attempt consumes provider-reported tokens before returning `FAILED`, `TIMED_OUT`, or `QUESTIONING`
- WHEN loop progress is saved for the incomplete selected change
- THEN `LoopProgress.tokenUsage` MUST include the tokens consumed by that phase attempt
- AND the selected change MUST remain incomplete

#### Scenario: retry adds only newly consumed tokens
- GIVEN persisted progress already contains cumulative token usage from an interrupted phase attempt
- AND the loop resumes the same selected change from its active phase checkpoint
- WHEN the interrupted phase is retried and consumes additional tokens
- THEN the newly saved `LoopProgress.tokenUsage` MUST equal the recovered cumulative usage plus the retry attempt token usage
- AND it MUST NOT add the recovered cumulative usage a second time as new consumption

#### Scenario: skipped completed phases are not charged again
- GIVEN persisted progress contains an active phase checkpoint with one or more completed phases
- AND persisted progress includes the cumulative token usage consumed before recovery
- WHEN the loop resumes and skips the completed phases
- THEN skipped phases MUST NOT add token usage during the resumed execution
- AND only newly executed phases MAY increase `LoopProgress.tokenUsage`

### Requirement: Context recovery on start

Previously: recovery initialized context budgeting from persisted
`LoopProgress.tokenUsage` when the value was greater than zero.

Context recovery MUST apply the persisted cumulative token usage exactly once as
the budget baseline before any checkpointed phase or newly selected candidate
executes.

#### Scenario: recovered baseline survives fresh phase sessions
- GIVEN persisted progress contains `tokenUsage` greater than zero
- AND persisted progress contains an active phase checkpoint
- WHEN the loop starts with context budgeting enabled
- THEN the context budget tracker MUST start from the persisted cumulative token usage
- AND the fresh phase execution context for the resumed phase MUST NOT reset that cumulative baseline

#### Scenario: no double counting after restart
- GIVEN persisted progress contains `tokenUsage` greater than zero
- WHEN the loop starts and no new phase work has executed yet
- THEN saving progress again MUST preserve the recovered token usage value
- AND MUST NOT increase it solely because recovery initialized the budget tracker

### Requirement: Context exhaustion detection in DefaultLoopDriver

Previously: context exhaustion saved progress, published `LOOP_CONTEXT_EXHAUSTED`,
and stopped the loop when remaining capacity fell below the configured threshold.

When context exhaustion happens while a selected roadmap change is incomplete,
the saved progress MUST preserve both the latest cumulative token usage and the
retryable active phase checkpoint.

#### Scenario: exhaustion preserves retryable checkpoint
- GIVEN a loop iteration has selected a roadmap change
- AND one or more phases have completed successfully
- AND context exhaustion is detected before all phases complete
- WHEN the loop saves progress before stopping
- THEN persisted progress MUST include the latest cumulative token usage
- AND persisted progress MUST include an active phase checkpoint for the selected change
- AND the checkpoint MUST include only the phases completed before exhaustion
- AND the selected change name MUST NOT be added to `completedChangeNames`

#### Scenario: exhaustion event reports cumulative usage
- GIVEN context exhaustion is detected after recovery from a previous progress snapshot
- WHEN the loop publishes `LOOP_CONTEXT_EXHAUSTED`
- THEN the event metadata `tokenUsage` MUST reflect recovered cumulative usage plus newly consumed phase tokens
- AND it MUST NOT report only the current phase attempt token usage

### Requirement: Phase checkpoint recovery

Previously: phase checkpoint recovery resumed the checkpointed candidate before
selecting a new roadmap candidate and skipped completed phases.

Phase checkpoint recovery MUST preserve context-budget accounting across the
same resume boundary.

#### Scenario: checkpoint recovery uses same budget lineage
- GIVEN persisted progress contains an active phase checkpoint and cumulative token usage
- WHEN the loop resumes the checkpointed candidate
- THEN the resumed execution MUST belong to the same cumulative budget lineage
- AND successful completion of the remaining phases MUST persist token usage equal to the recovered baseline plus newly executed phase token usage

#### Scenario: completed candidate clears checkpoint but keeps token usage
- GIVEN a checkpointed candidate resumes and all remaining phases complete successfully
- WHEN completed iteration progress is saved
- THEN the active phase checkpoint MUST be cleared
- AND the completed change name MUST be present in `completedChangeNames`
- AND `LoopProgress.tokenUsage` MUST preserve the final cumulative token usage for the loop lineage

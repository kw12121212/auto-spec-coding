---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/WorkflowStepResult.java
    - src/main/java/org/specdriven/sdk/WorkflowRuntime.java
    - src/main/java/org/specdriven/agent/event/EventType.java
  tests:
    - src/test/java/org/specdriven/sdk/WorkflowStepCompositionTest.java
    - src/test/java/org/specdriven/agent/event/EventSystemTest.java
---

# Workflow Step Composition

## ADDED Requirements

### Requirement: Retryable step failure result

The system MUST support a retryable workflow step failure result that is distinguishable from a
terminal step failure.

#### Scenario: Retryable failure is distinguishable from terminal failure
- GIVEN a `WorkflowStepResult` created via `retryableFailure("timeout")`
- THEN `isFailure()` MUST return `true`
- AND `isRetryableFailure()` MUST return `true`
- AND `failureReason()` MUST return `"timeout"`

#### Scenario: Terminal failure remains non-retryable
- GIVEN a `WorkflowStepResult` created via `failure("timeout")`
- THEN `isFailure()` MUST return `true`
- AND `isRetryableFailure()` MUST return `false`

### Requirement: Retry preserves step order

When a workflow step is retried, the retry MUST occur at the same declared step position before any
later step executes.

#### Scenario: Later steps wait for the retried step
- GIVEN step A reports a retryable failure on its first attempt and succeeds on a later supported retry
- WHEN the workflow completes
- THEN step B MUST NOT execute before step A eventually succeeds or the retry policy is exhausted
- AND any later successful output MUST still flow from the final successful step A attempt

## MODIFIED Requirements

### Requirement: Step failure terminates the workflow

Previously: The system MUST terminate the workflow as FAILED when any step fails, without executing subsequent steps.

The system MUST terminate the workflow as FAILED without executing subsequent steps when a step
fails terminally, or when a retryable step failure exhausts the supported retry policy.

#### Scenario: Non-retryable step failure stops execution
- GIVEN a workflow with two steps where step A fails with terminal failure `"connection refused"`
- WHEN step A fails
- THEN the runtime MUST NOT execute step B
- AND the workflow MUST transition to `FAILED`
- AND the failure summary MUST identify that step A failed and include the failure reason

#### Scenario: Retryable failure exhausts policy before later steps run
- GIVEN a workflow with two steps where step A keeps returning retryable failure `"connection refused"`
- WHEN the supported retry policy is exhausted
- THEN step B MUST NOT execute
- AND the workflow MUST transition to `FAILED`
- AND the failure summary MUST indicate that step A failed after retry exhaustion

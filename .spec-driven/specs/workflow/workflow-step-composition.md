---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/WorkflowStep.java
    - src/main/java/org/specdriven/sdk/WorkflowStepExecutor.java
    - src/main/java/org/specdriven/sdk/WorkflowStepResult.java
    - src/main/java/org/specdriven/sdk/WorkflowRuntime.java
    - src/main/java/org/specdriven/agent/event/EventType.java
  tests:
    - src/test/java/org/specdriven/sdk/WorkflowStepCompositionTest.java
    - src/test/java/org/specdriven/agent/event/EventSystemTest.java
---

# Workflow Step Composition

## Requirements

### Requirement: Step descriptor model

The system MUST support a step descriptor that declares a step type and a target name within a workflow declaration.

#### Scenario: Step descriptor is validated at declaration time
- GIVEN a workflow declaration contains a step descriptor with a blank target name
- WHEN registration is attempted through either supported declaration path
- THEN registration MUST fail explicitly
- AND that workflow MUST NOT become startable

#### Scenario: Supported step types
- THEN a step descriptor MUST support at least the following step types: `service`, `tool`, `agent`

---

### Requirement: Sequential step execution

The system MUST execute workflow steps in declaration order, one at a time.

#### Scenario: Steps execute in order
- GIVEN a workflow with three steps declared in order: step A, step B, step C
- WHEN the workflow is started and all steps succeed
- THEN the runtime MUST complete step A before starting step B
- AND the runtime MUST complete step B before starting step C

#### Scenario: Previous step output is available to the next step
- GIVEN a workflow with two steps where step A produces output `{"key": "value"}`
- WHEN step A completes successfully
- THEN step B MUST receive input that includes `{"key": "value"}`

---

### Requirement: Step failure terminates the workflow

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

---

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

---

### Requirement: Step audit events

The system MUST publish audit events for each step's lifecycle transitions.

#### Scenario: Step start event is published before execution
- GIVEN a workflow step is about to be dispatched
- THEN the system MUST publish a `WORKFLOW_STEP_STARTED` event before invoking the step executor
- AND the event MUST include the workflow instance id, step index, step type, and step target name

#### Scenario: Step completion event is published after success
- GIVEN a workflow step completes without error
- THEN the system MUST publish a `WORKFLOW_STEP_COMPLETED` event
- AND the event MUST include the workflow instance id, step index, step type, and step target name

#### Scenario: Step failure event is published after error
- GIVEN a workflow step throws an exception or returns a failure result
- THEN the system MUST publish a `WORKFLOW_STEP_FAILED` event
- AND the event MUST include the workflow instance id, step index, step type, step target name, and failure reason

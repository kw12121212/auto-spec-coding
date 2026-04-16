---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/WorkflowRuntime.java
    - src/main/java/org/specdriven/sdk/WorkflowResultView.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
    - src/main/java/org/specdriven/agent/event/EventType.java
  tests:
    - src/test/java/org/specdriven/sdk/WorkflowRuntimeTest.java
    - src/test/java/org/specdriven/sdk/WorkflowAgentHumanBridgeTest.java
    - src/test/java/org/specdriven/agent/event/EventSystemTest.java
---

# Workflow Runtime

## ADDED Requirements

### Requirement: Workflow checkpoint recovery contract

The system MUST preserve a recoverable workflow checkpoint for supported non-terminal workflow
instances so that execution can continue after a supported runtime interruption.

#### Scenario: Recovery resumes from the first incomplete step
- GIVEN a workflow with three declared steps where step 0 completed successfully
- AND step 1 had not completed when a supported runtime interruption occurred
- WHEN the workflow runtime is reconstructed against the same persisted workflow state
- THEN the workflow MUST remain queryable under the same `workflowId`
- AND recovery MUST resume with step 1 rather than re-running step 0
- AND later steps MUST NOT run before step 1 completes

#### Scenario: Waiting workflow remains correlated across recovery
- GIVEN a workflow reached `WAITING_FOR_INPUT` after dispatching a question
- WHEN the workflow runtime is reconstructed against the same persisted workflow state before an answer arrives
- THEN the workflow MUST continue to report `WAITING_FOR_INPUT`
- AND the pending question correlation MUST still resolve through the same `workflowId`
- AND a later matching answer MUST resume that same workflow instance rather than creating a new one

### Requirement: Workflow runtime-driven retry contract

The system MUST automatically retry the current workflow step when the step reports a supported
retryable failure and the supported retry policy allows another attempt.

#### Scenario: Retryable step failure re-executes the same step
- GIVEN a workflow step reports a retryable failure on its first attempt
- AND the supported retry policy allows another attempt
- WHEN the runtime handles that failure
- THEN the workflow MUST keep the same `workflowId`
- AND the runtime MUST retry the same step before any later step executes
- AND the workflow MUST NOT transition to `FAILED` before the retry policy is exhausted or a non-retryable failure occurs

#### Scenario: Exhausted retry policy ends workflow with diagnosable failure
- GIVEN a workflow step continues to report a retryable failure until the supported retry policy is exhausted
- WHEN the final retry attempt fails
- THEN the workflow MUST transition to `FAILED`
- AND the final failure view MUST identify the failed step and the last stable failure reason
- AND the final failure view MUST indicate that the supported retry policy was exhausted

### Requirement: Workflow recovery observability

The system MUST publish observable audit signals when it saves a recoverable workflow checkpoint,
recovers a checkpointed workflow, or schedules a retry for a retryable step failure.

#### Scenario: Checkpoint save is auditable
- GIVEN a non-terminal workflow reaches a recoverable execution boundary
- WHEN the runtime persists that recoverable state
- THEN the system MUST publish `WORKFLOW_CHECKPOINT_SAVED`
- AND the event MUST identify the workflow instance and the resume boundary

#### Scenario: Recovery is auditable
- GIVEN a workflow is resumed from persisted recoverable state
- WHEN recovery begins
- THEN the system MUST publish `WORKFLOW_RECOVERED`
- AND the event MUST identify the workflow instance and the resumed execution boundary

#### Scenario: Retry scheduling is auditable
- GIVEN a step failure is classified as retryable and another attempt will be made
- WHEN the runtime schedules the retry
- THEN the system MUST publish `WORKFLOW_STEP_RETRY_SCHEDULED`
- AND the event MUST identify the workflow instance, step, attempt number, and failure reason

## MODIFIED Requirements

### Requirement: Workflow result contract

Previously: The system MUST provide a workflow result view for previously started workflow instances.

The system MUST provide a workflow result view for previously started workflow instances,
including diagnosable failure information for supported retry and recovery behavior.

#### Scenario: Failed workflow returns diagnosable failure view
- GIVEN a previously started workflow instance whose status is `FAILED`
- WHEN a caller reads the workflow result view
- THEN the returned view MUST expose status `FAILED`
- AND it MUST expose a failure summary instead of a successful result payload
- AND the failure summary MUST identify the failed step when the failure originated from step execution
- AND it MUST include the last stable failure reason
- AND it MUST indicate retry exhaustion when supported retries were attempted and exhausted

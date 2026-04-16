---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/WorkflowStepResult.java
    - src/main/java/org/specdriven/sdk/WorkflowRuntime.java
    - src/main/java/org/specdriven/agent/event/EventType.java
    - src/main/java/org/specdriven/agent/question/QuestionEvents.java
  tests:
    - src/test/java/org/specdriven/sdk/WorkflowAgentHumanBridgeTest.java
    - src/test/java/org/specdriven/agent/event/EventSystemTest.java
---

## ADDED Requirements

### Requirement: Awaiting-input step result variant

The system MUST support an awaiting-input step result that signals `WorkflowRuntime` to pause the
workflow for human input.

#### Scenario: Awaiting-input result is distinguishable from success and failure
- GIVEN a `WorkflowStepResult` created via `awaitingInput(prompt)`
- THEN `isAwaitingInput()` MUST return `true`
- AND `isFailure()` MUST return `false`
- AND `inputPrompt()` MUST return the provided non-blank prompt string

#### Scenario: Success result is not awaiting input
- GIVEN a `WorkflowStepResult` created via `success(output)`
- THEN `isAwaitingInput()` MUST return `false`

#### Scenario: Failure result is not awaiting input
- GIVEN a `WorkflowStepResult` created via `failure(reason)`
- THEN `isAwaitingInput()` MUST return `false`

---

### Requirement: Workflow pause contract

When a workflow step returns an awaiting-input result, the system MUST pause the workflow and
dispatch a structured question via the configured question delivery surface.

#### Scenario: Awaiting-input step transitions workflow to WAITING_FOR_INPUT
- GIVEN a workflow with a step that returns `awaitingInput("Please confirm the invoice amount")`
- WHEN that step executes during workflow advancement
- THEN the workflow MUST transition to `WAITING_FOR_INPUT`
- AND a `WORKFLOW_PAUSED_FOR_INPUT` event MUST be published
- AND the event MUST include `workflowId`, `questionId`, and `prompt`

#### Scenario: Workflow without a question delivery surface fails on awaiting-input
- GIVEN a `WorkflowRuntime` constructed without a configured `QuestionDeliveryService`
- AND a workflow with a step that returns `awaitingInput(prompt)`
- WHEN that step executes
- THEN the workflow MUST transition to `FAILED`
- AND the failure summary MUST indicate that no question delivery surface is configured

---

### Requirement: Workflow resume contract

When a `QUESTION_ANSWERED` event arrives for a paused workflow, the system MUST resume execution
with the answer content injected into the next step's input context.

#### Scenario: Answer resumes workflow to RUNNING
- GIVEN a workflow in `WAITING_FOR_INPUT` with a pending question dispatched as `sessionId = workflowId`
- WHEN a `QUESTION_ANSWERED` event is observed with matching `sessionId`
- THEN the workflow MUST transition from `WAITING_FOR_INPUT` to `RUNNING`
- AND a `WORKFLOW_RESUMED` event MUST be published

#### Scenario: Answer content is injected as humanInput for the next step
- GIVEN a workflow resumed after a pause with answer content `"approved"`
- WHEN the next step executes
- THEN the step input context MUST include `"humanInput" → "approved"`

#### Scenario: Resumed workflow that has no further steps succeeds
- GIVEN a workflow paused at its last step
- WHEN the workflow is resumed
- THEN the workflow MUST transition from `RUNNING` to `SUCCEEDED`

---

### Requirement: Workflow pause audit events

The system MUST emit observable audit events for workflow pause and resume transitions.

#### Scenario: WORKFLOW_PAUSED_FOR_INPUT event is emitted on pause
- GIVEN a workflow that pauses for human input
- THEN the system MUST emit a `WORKFLOW_PAUSED_FOR_INPUT` event
- AND the event MUST include `workflowId`, `questionId`, and `prompt`

#### Scenario: WORKFLOW_RESUMED event is emitted on resume
- GIVEN a workflow in `WAITING_FOR_INPUT` that receives a matching answer
- THEN the system MUST emit a `WORKFLOW_RESUMED` event
- AND the event MUST include `workflowId` and `questionId`

---

### Requirement: Question correlation via workflow instance ID

The system MUST use the workflow instance ID as the `sessionId` when creating questions for a
paused workflow, so that answer events can be matched back to the correct workflow instance.

#### Scenario: Dispatched question uses workflowId as sessionId
- GIVEN a workflow instance with `workflowId = "wf-123"` that pauses for input
- WHEN a question is created and delivered
- THEN the question's `sessionId` MUST equal `"wf-123"`

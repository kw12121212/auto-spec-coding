---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/WorkflowRuntime.java
    - src/main/java/org/specdriven/sdk/WorkflowStatus.java
    - src/main/java/org/specdriven/sdk/WorkflowInstanceView.java
    - src/main/java/org/specdriven/sdk/WorkflowResultView.java
    - src/main/java/org/specdriven/sdk/WorkflowStep.java
    - src/main/java/org/specdriven/sdk/WorkflowStepExecutor.java
    - src/main/java/org/specdriven/sdk/WorkflowStepResult.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
    - src/main/java/org/specdriven/agent/event/EventType.java
    - src/main/java/org/specdriven/agent/question/QuestionEvents.java
  tests:
    - src/test/java/org/specdriven/sdk/WorkflowRuntimeTest.java
    - src/test/java/org/specdriven/sdk/WorkflowStepCompositionTest.java
    - src/test/java/org/specdriven/sdk/WorkflowAgentHumanBridgeTest.java
    - src/test/java/org/specdriven/agent/event/EventSystemTest.java
---

# Workflow Runtime

## Requirements

### Requirement: Workflow declaration parity

The system MUST support a first workflow declaration contract through both an in-process domain declaration path and a governed Lealone SQL declaration path. Either path MUST accept an optional ordered list of step descriptors at declaration time. A step descriptor MUST declare a step type (`service`, `tool`, or `agent`) and a target name.

#### Scenario: Domain declaration makes a workflow startable
- GIVEN application code registers a supported workflow declaration named `invoice-approval` through the supported SDK or domain declaration path
- WHEN registration succeeds
- THEN `invoice-approval` MUST become startable through the normal workflow runtime contract

#### Scenario: SQL declaration makes a workflow startable
- GIVEN a supported Lealone SQL `CREATE WORKFLOW` declaration for `invoice-approval`
- WHEN that declaration is applied through the supported workflow declaration path
- THEN `invoice-approval` MUST become startable through the normal workflow runtime contract

#### Scenario: Unsupported workflow declaration is rejected atomically
- GIVEN a workflow declaration input that is unsupported or malformed for the first workflow contract
- WHEN registration is attempted through either supported declaration path
- THEN registration MUST fail explicitly
- AND that workflow MUST NOT become startable through the workflow runtime contract

#### Scenario: Domain declaration with steps makes a workflow startable with step execution
- GIVEN application code registers a workflow named `order-process` with two step descriptors — a `tool` step targeting `read-file` and a `service` step targeting `invoice-svc`
- WHEN the workflow is started
- THEN the runtime MUST execute the `read-file` tool step first, then the `invoice-svc` service step in declaration order
- AND the workflow MUST eventually reach `SUCCEEDED` or `FAILED`

#### Scenario: Workflow with no steps succeeds without executing any step
- GIVEN a workflow is declared with an empty step list
- WHEN the workflow is started
- THEN the runtime MUST reach `SUCCEEDED` without invoking any step executor

### Requirement: Workflow instance status model

The system MUST expose a stable workflow instance status model for the first workflow runtime contract.

#### Scenario: Required workflow instance statuses
- THEN the workflow instance status model MUST include `ACCEPTED`
- AND it MUST include `RUNNING`
- AND it MUST include `WAITING_FOR_INPUT`
- AND it MUST include `SUCCEEDED`
- AND it MUST include `FAILED`
- AND it MUST include `CANCELLED`

### Requirement: Workflow instance identity contract

The system MUST expose workflow instance views using stable identity and lifecycle fields.

#### Scenario: Started workflow exposes stable identifiers
- GIVEN a successfully started workflow instance
- THEN the instance view MUST expose a non-blank `workflowId`
- AND it MUST expose the declared workflow name
- AND it MUST expose the current workflow status
- AND it MUST expose creation and update timestamps

### Requirement: Workflow start contract

The system MUST provide a workflow start contract that starts a previously declared workflow by name using supported structured input.

#### Scenario: Start declared workflow by name
- GIVEN a declared workflow named `invoice-approval`
- WHEN a caller starts `invoice-approval` with supported structured input
- THEN the system MUST return a workflow instance view with a non-blank `workflowId`
- AND the returned status MUST be `ACCEPTED`

#### Scenario: Unknown workflow name is rejected
- GIVEN no declared workflow named `invoice-approval`
- WHEN a caller attempts to start `invoice-approval`
- THEN the system MUST fail explicitly
- AND it MUST NOT report a started workflow instance

### Requirement: Workflow state query contract

The system MUST provide a workflow state query contract for previously started workflow instances.

#### Scenario: Query returns current workflow status
- GIVEN a previously started workflow instance
- WHEN a caller queries that workflow instance by `workflowId`
- THEN the system MUST return a state view for the same `workflowId`
- AND the state view MUST expose the current workflow status

#### Scenario: Unknown workflow instance is rejected
- GIVEN no workflow instance exists for the requested `workflowId`
- WHEN a caller queries workflow state
- THEN the system MUST fail explicitly

### Requirement: Workflow result contract

The system MUST provide a workflow result view for previously started workflow instances.

#### Scenario: Non-terminal workflow returns no final result
- GIVEN a previously started workflow instance whose status is `ACCEPTED`, `RUNNING`, or `WAITING_FOR_INPUT`
- WHEN a caller reads the workflow result view
- THEN the returned view MUST expose the current workflow status
- AND the final result payload MUST be null or empty

#### Scenario: Successful workflow returns final result
- GIVEN a previously started workflow instance whose status is `SUCCEEDED`
- WHEN a caller reads the workflow result view
- THEN the returned view MUST expose status `SUCCEEDED`
- AND it MUST include the final workflow result payload

#### Scenario: Failed workflow returns failure view
- GIVEN a previously started workflow instance whose status is `FAILED` or `CANCELLED`
- WHEN a caller reads the workflow result view
- THEN the returned view MUST expose the terminal workflow status
- AND it MUST expose a failure or cancellation summary instead of a successful result payload

### Requirement: Workflow lifecycle transition contract

The system MUST preserve a stable observable workflow instance lifecycle.

#### Scenario: Successful workflow progresses to completion
- GIVEN a workflow instance in `ACCEPTED`
- WHEN the workflow runtime begins and completes supported execution successfully
- THEN the observable lifecycle MUST progress through `RUNNING`
- AND it MUST end in `SUCCEEDED`

#### Scenario: Waiting workflow can resume
- GIVEN a workflow instance that reaches `WAITING_FOR_INPUT`
- WHEN the supported runtime later resumes the workflow instance
- THEN the observable lifecycle MUST return to `RUNNING` before reaching its next terminal or waiting state

#### Scenario: Terminal workflow state is stable
- GIVEN a workflow instance in `SUCCEEDED`, `FAILED`, or `CANCELLED`
- WHEN a caller later queries its state or result view
- THEN the workflow instance MUST remain in the same terminal status

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

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
  tests:
    - src/test/java/org/specdriven/sdk/WorkflowRuntimeTest.java
    - src/test/java/org/specdriven/sdk/WorkflowStepCompositionTest.java
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

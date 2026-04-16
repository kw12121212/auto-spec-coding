---
mapping:
  implementation:
    - src/main/java/org/specdriven/sdk/WorkflowRuntime.java
    - src/main/java/org/specdriven/sdk/WorkflowStep.java
    - src/main/java/org/specdriven/sdk/WorkflowStepExecutor.java
    - src/main/java/org/specdriven/sdk/WorkflowStepResult.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
  tests:
    - src/test/java/org/specdriven/sdk/WorkflowStepCompositionTest.java
---

## MODIFIED Requirements

### Requirement: Workflow declaration parity

Previously: The system MUST support a first workflow declaration contract through both an in-process domain declaration path and a governed Lealone SQL declaration path.

The system MUST support a first workflow declaration contract through both an in-process domain declaration path and a governed Lealone SQL declaration path. Either path MUST accept an optional ordered list of step descriptors at declaration time. A step descriptor MUST declare a step type (`service`, `tool`, or `agent`) and a target name.

#### Scenario: Domain declaration with steps makes a workflow startable with step execution
- GIVEN application code registers a workflow named `order-process` with two step descriptors — a `tool` step targeting `read-file` and a `service` step targeting `invoice-svc`
- WHEN the workflow is started
- THEN the runtime MUST execute the `read-file` tool step first, then the `invoice-svc` service step in declaration order
- AND the workflow MUST eventually reach `SUCCEEDED` or `FAILED`

#### Scenario: Workflow with no steps succeeds without executing any step
- GIVEN a workflow is declared with an empty step list
- WHEN the workflow is started
- THEN the runtime MUST reach `SUCCEEDED` without invoking any step executor

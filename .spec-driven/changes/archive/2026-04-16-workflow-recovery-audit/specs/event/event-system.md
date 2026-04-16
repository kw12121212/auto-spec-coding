---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/event/EventType.java
    - src/main/java/org/specdriven/sdk/WorkflowRuntime.java
  tests:
    - src/test/java/org/specdriven/agent/event/EventSystemTest.java
    - src/test/java/org/specdriven/sdk/WorkflowRuntimeTest.java
    - src/test/java/org/specdriven/sdk/WorkflowStepCompositionTest.java
---

# Event System

## ADDED Requirements

### Requirement: Workflow recovery audit event metadata

Workflow recovery audit events MUST remain compatible with the existing event JSON serialization
constraints.

#### Scenario: Checkpoint saved event is auditable
- GIVEN the runtime emits `WORKFLOW_CHECKPOINT_SAVED`
- THEN the event metadata MUST include `workflowId`
- AND it MUST include `workflowName`
- AND it MUST include `status`
- AND it MUST include `resumeFromStepIndex`

#### Scenario: Workflow recovered event is auditable
- GIVEN the runtime emits `WORKFLOW_RECOVERED`
- THEN the event metadata MUST include `workflowId`
- AND it MUST include `workflowName`
- AND it MUST include `status`
- AND it MUST include `resumeFromStepIndex`

#### Scenario: Retry scheduled event is auditable
- GIVEN the runtime emits `WORKFLOW_STEP_RETRY_SCHEDULED`
- THEN the event metadata MUST include `workflowId`
- AND it MUST include `workflowName`
- AND it MUST include `stepIndex`
- AND it MUST include `stepName`
- AND it MUST include `attemptNumber`
- AND it MUST include `failureReason`

## MODIFIED Requirements

### Requirement: EventType enum

Previously: The event type model additionally defined `WORKFLOW_DECLARED`, `WORKFLOW_STARTED`,
`WORKFLOW_STATE_CHANGED`, `WORKFLOW_COMPLETED`, and `WORKFLOW_FAILED`.

The event type model MUST additionally define `WORKFLOW_DECLARED`, `WORKFLOW_STARTED`,
`WORKFLOW_STATE_CHANGED`, `WORKFLOW_COMPLETED`, `WORKFLOW_FAILED`,
`WORKFLOW_CHECKPOINT_SAVED`, `WORKFLOW_RECOVERED`, and `WORKFLOW_STEP_RETRY_SCHEDULED`.

### Requirement: Workflow runtime audit event metadata

Previously: Workflow runtime audit events MUST remain compatible with the existing event JSON serialization constraints.

Workflow runtime audit events MUST remain compatible with the existing event JSON serialization
constraints, including runtime-driven retry exhaustion diagnostics when a workflow ultimately fails.

#### Scenario: Workflow failure after retry exhaustion is auditable
- GIVEN a workflow reaches `FAILED` after the supported retry policy for a step is exhausted
- WHEN the runtime emits `WORKFLOW_FAILED`
- THEN the event metadata MUST include `workflowId`
- AND it MUST include `workflowName`
- AND it MUST include a stable `failureReason`
- AND it MUST include `retryExhausted` with value `true`
- AND it MUST include `failedStepName`

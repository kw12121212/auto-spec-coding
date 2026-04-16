---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/event/EventType.java
    - src/main/java/org/specdriven/agent/event/Event.java
    - src/main/java/org/specdriven/agent/event/LealoneAuditLogStore.java
    - src/main/java/org/specdriven/sdk/WorkflowRuntime.java
  tests:
    - src/test/java/org/specdriven/agent/event/EventSystemTest.java
    - src/test/java/org/specdriven/sdk/WorkflowRuntimeTest.java
    - src/test/java/org/specdriven/agent/event/LealoneAuditLogStoreTest.java
---

## MODIFIED Requirements

### Requirement: EventType enum
Previously: `EventType` MUST define the existing tool, agent, task, team, cron, background-process, question, LLM-config, interactive, skill hot-load, and platform event names already listed in the main spec.
`EventType` MUST continue to define those existing event names and MUST additionally define `WORKFLOW_DECLARED`, `WORKFLOW_STARTED`, `WORKFLOW_STATE_CHANGED`, `WORKFLOW_COMPLETED`, and `WORKFLOW_FAILED`.

## ADDED Requirements

### Requirement: Workflow runtime audit event metadata

Workflow runtime audit events MUST remain compatible with the existing event JSON serialization constraints.

#### Scenario: Workflow declaration event is auditable
- GIVEN a workflow declaration is accepted through a supported declaration path
- WHEN the runtime emits `WORKFLOW_DECLARED`
- THEN the event metadata MUST include `workflowName`
- AND it MUST identify which declaration path was used

#### Scenario: Workflow start and state change events are auditable
- GIVEN a workflow instance is started and later changes status
- WHEN the runtime emits `WORKFLOW_STARTED` or `WORKFLOW_STATE_CHANGED`
- THEN the event metadata MUST include `workflowId`
- AND it MUST include `workflowName`
- AND `WORKFLOW_STATE_CHANGED` metadata MUST include `fromStatus` and `toStatus`

#### Scenario: Workflow failure event is auditable
- GIVEN a workflow instance ends in `FAILED`
- WHEN the runtime emits `WORKFLOW_FAILED`
- THEN the event metadata MUST include `workflowId`
- AND it MUST include `workflowName`
- AND it MUST include a stable `failureReason`

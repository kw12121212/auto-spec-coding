---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/event/Event.java
    - src/main/java/org/specdriven/agent/event/EventType.java
    - src/main/java/org/specdriven/sdk/LealonePlatform.java
  tests:
    - src/test/java/org/specdriven/agent/event/EventSystemTest.java
    - src/test/java/org/specdriven/sdk/LealonePlatformTest.java
    - src/test/java/org/specdriven/sdk/PlatformHealthTest.java
---

## MODIFIED Requirements

### Requirement: EventType enum
Previously: The event model defined the existing platform, workflow, interactive, cache, and background-process event types.
The event model MUST additionally define `PROFILE_EXECUTION_RECORDED` for audit visibility of Sandlock-backed profile execution attempts.

## ADDED Requirements

### Requirement: Profile execution audit event metadata
`PROFILE_EXECUTION_RECORDED` events MUST remain compatible with the existing event JSON serialization constraints.

- Metadata MUST include a stable `outcome`
- Metadata MUST include the executed command in a stable rendered form
- Metadata MUST include `resolvedProfile` when execution resolved a profile successfully
- Metadata MUST include `requestedProfile` when the caller supplied an explicit profile name
- Metadata MUST include `exitCode` when a command launched and completed
- Metadata MUST include `failureCode` when execution failed before command completion

#### Scenario: successful profile execution is auditable
- GIVEN a Sandlock-backed command completes under a resolved profile
- WHEN the system emits `PROFILE_EXECUTION_RECORDED`
- THEN the event metadata MUST include `outcome`
- AND it MUST include `resolvedProfile`
- AND it MUST include `exitCode`

#### Scenario: failed profile launch is auditable
- GIVEN a Sandlock-backed execution attempt fails before completion
- WHEN the system emits `PROFILE_EXECUTION_RECORDED`
- THEN the event metadata MUST include `outcome`
- AND it MUST include `failureCode`
- AND it MUST include `requestedProfile` when the caller supplied one

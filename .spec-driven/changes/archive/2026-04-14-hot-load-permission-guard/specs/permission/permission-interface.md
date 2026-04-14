---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/permission/DefaultPermissionProvider.java
    - src/main/java/org/specdriven/agent/permission/Permission.java
    - src/main/java/org/specdriven/agent/permission/PermissionContext.java
    - src/main/java/org/specdriven/agent/permission/PermissionDecision.java
    - src/main/java/org/specdriven/agent/permission/PermissionProvider.java
  tests:
    - src/test/java/org/specdriven/agent/permission/DefaultPermissionProviderWithStoreTest.java
    - src/test/java/org/specdriven/agent/permission/PermissionProviderTest.java
---

# permission-interface.md - delta for hot-load-permission-guard

## MODIFIED Requirements

### Requirement: PermissionProvider contract

- Existing `PermissionDecision.ALLOW`, `PermissionDecision.DENY`, and `PermissionDecision.CONFIRM` meanings MUST remain unchanged for hot-load permissions
- Hot-load permission checks MUST use ordinary `Permission` and `PermissionContext` values

### Requirement: Default permission policy behavior

- The default permission policy MUST return `DENY` for hot-load actions unless an explicit stored policy grants the requested permission

## ADDED Requirements

### Requirement: Hot-load permission naming

- `skill.hotload.load` MUST represent permission to load and potentially compile a dynamic skill executor for activation
- `skill.hotload.replace` MUST represent permission to replace and potentially compile a dynamic skill executor for activation
- `skill.hotload.unload` MUST represent permission to unload an active dynamic skill executor
- Hot-load permission resources MUST use the form `skill:<skillName>`
- Hot-load permission constraints MAY include operation metadata such as `entryClassName` or `sourceHash`, but constraints MUST NOT include raw Java source

## ADDED Scenarios

#### Scenario: default policy denies hot-load action without stored grant

- GIVEN a default permission provider with no stored policy for a hot-load action
- WHEN the provider checks `skill.hotload.load` on `skill:<skillName>`
- THEN it MUST return `DENY`

#### Scenario: stored policy can allow hot-load action

- GIVEN a default permission provider backed by a policy store
- AND the policy store contains an `ALLOW` decision for `skill.hotload.replace` on `skill:<skillName>` for the requester
- WHEN the provider checks that permission and context
- THEN it MUST return `ALLOW`

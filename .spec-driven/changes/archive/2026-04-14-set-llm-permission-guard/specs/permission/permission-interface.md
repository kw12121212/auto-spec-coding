---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/permission/DefaultPermissionProvider.java
  tests:
    - src/test/java/org/specdriven/agent/permission/DefaultPermissionProviderWithStoreTest.java
---

# Permission Interface — LLM Config Permission Delta

## ADDED Requirements

### Requirement: LLM config mutation permission action

The permission action `llm.config.set` MUST represent the right to modify runtime LLM configuration through `SET LLM` statements or equivalent mutation paths.

- `llm.config.set` MUST represent permission to change runtime LLM config for a targeted scope
- LLM config permission resources MUST use the form `session:<sessionId>` for session-scoped changes
- LLM config permission constraints MAY include operation metadata such as the mutation type

### Requirement: Default-deny policy for LLM config mutations

The default permission policy MUST return `DENY` for `llm.config.set` actions unless an explicit stored policy grants the requested permission.

#### Scenario: default policy denies LLM config mutation without stored grant
- GIVEN a default permission provider with no stored policy for an LLM config action
- WHEN the provider checks `llm.config.set` on `session:<sessionId>`
- THEN it MUST return `DENY`

#### Scenario: stored policy can allow LLM config mutation
- GIVEN a default permission provider backed by a policy store
- AND the policy store contains an `ALLOW` decision for `llm.config.set` on `session:<sessionId>` for the requester
- WHEN the provider checks that permission and context
- THEN it MUST return `ALLOW`

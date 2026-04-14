---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/event/EventType.java
    - src/main/java/org/specdriven/agent/agent/DefaultLlmProviderRegistry.java
  tests:
    - src/test/java/org/specdriven/agent/agent/DefaultLlmProviderRegistryTest.java
---

## ADDED Requirements

### Requirement: Failed config change audit event
The system MUST publish an `LLM_CONFIG_CHANGE_REJECTED` event whenever a runtime LLM config change attempt fails due to permission denial, confirmation requirement, validation error, or parsing failure before a replacement snapshot becomes active.

#### Scenario: Permission denied publishes rejected event
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `DENY` for `llm.config.set`
- WHEN `applySetLlmStatement("session-a", "SET LLM model = 'gpt-4'")` is called
- THEN exactly one `LLM_CONFIG_CHANGE_REJECTED` event MUST be published before the `SetLlmSqlException` is thrown
- AND the event metadata MUST contain `scope = "session"`, `sessionId = "session-a"`, `result = "denied"`, and a non-empty `reason`

#### Scenario: Confirm required publishes rejected event
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `CONFIRM` for `llm.config.set`
- WHEN `applySetLlmStatement("session-a", "SET LLM model = 'gpt-4'")` is called
- THEN exactly one `LLM_CONFIG_CHANGE_REJECTED` event MUST be published
- AND the event metadata MUST contain `result = "confirm_required"`

#### Scenario: Validation failure publishes rejected event
- GIVEN a `SET LLM` statement with an unsupported key or invalid value
- WHEN the statement is evaluated and fails validation
- THEN exactly one `LLM_CONFIG_CHANGE_REJECTED` event MUST be published
- AND the event metadata MUST contain `result = "validation_failed"` and a non-empty `reason`

#### Scenario: Parse error publishes rejected event
- GIVEN a malformed `SET LLM` SQL statement
- WHEN the statement cannot be parsed
- THEN exactly one `LLM_CONFIG_CHANGE_REJECTED` event MUST be published
- AND the event metadata MUST contain `result = "parse_error"` and a non-empty `reason`

#### Scenario: Clear session permission denied publishes rejected event
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `DENY` for `llm.config.set`
- AND session `session-a` has a session-specific snapshot override
- WHEN `clearSessionSnapshot("session-a")` is called
- THEN exactly one `LLM_CONFIG_CHANGE_REJECTED` event MUST be published before the exception is thrown
- AND the event metadata MUST contain `result = "denied"`

#### Scenario: Successful change does not publish rejected event
- GIVEN a successful `SET LLM` statement
- WHEN the replacement snapshot becomes active
- THEN no `LLM_CONFIG_CHANGE_REJECTED` event MUST be published

### Requirement: Config change event operator metadata
Both `LLM_CONFIG_CHANGED` and `LLM_CONFIG_CHANGE_REJECTED` events MUST include an `operator` field in their metadata identifying the initiator of the change attempt.

#### Scenario: Session-scoped success event includes session operator
- GIVEN a successful `SET LLM` statement in session `session-a`
- WHEN the `LLM_CONFIG_CHANGED` event is published
- THEN the event metadata MUST contain `operator = "session:session-a"`

#### Scenario: Default-scope success event includes system operator
- GIVEN a successful default snapshot replacement
- WHEN the `LLM_CONFIG_CHANGED` event is published
- THEN the event metadata MUST contain `operator = "system"`

#### Scenario: Session-scoped rejected event includes session operator
- GIVEN a `SET LLM` statement in session `session-a` that is rejected
- WHEN the `LLM_CONFIG_CHANGE_REJECTED` event is published
- THEN the event metadata MUST contain `operator = "session:session-a"`

### Requirement: Rejected event metadata excludes secrets
`LLM_CONFIG_CHANGE_REJECTED` event metadata MUST NOT contain API key values, resolved secret values, or vault reference names.

#### Scenario: Rejected event metadata does not contain API key
- GIVEN a provider with API key `sk-real-key` is active
- AND a `SET LLM` statement is rejected
- WHEN the `LLM_CONFIG_CHANGE_REJECTED` event metadata is inspected
- THEN no metadata value MUST equal `sk-real-key`

#### Scenario: Rejected event metadata does not contain vault reference
- GIVEN a provider was constructed from `vault:openai_key` reference
- AND a `SET LLM` statement is rejected
- WHEN the `LLM_CONFIG_CHANGE_REJECTED` event metadata is inspected
- THEN no metadata value MUST equal `vault:openai_key`

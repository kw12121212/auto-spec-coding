---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/LlmConfig.java
    - src/main/java/org/specdriven/agent/agent/DefaultLlmProviderRegistry.java
  tests:
    - src/test/java/org/specdriven/agent/agent/LlmConfigTest.java
    - src/test/java/org/specdriven/agent/agent/DefaultLlmProviderRegistryTest.java
---

## MODIFIED Requirements

### Requirement: LlmConfig toString redacts API key
Previously: `LlmConfig` used the default Java record `toString()` which included all fields in plaintext.
`LlmConfig.toString()` MUST return a string representation where the `apiKey` field is replaced with a fixed redaction placeholder, preventing the actual secret value from appearing in logs, debug output, or exception messages.

#### Scenario: toString hides API key
- GIVEN a `LlmConfig` with `apiKey` set to `sk-super-secret-key`
- WHEN `toString()` is called on that config
- THEN the returned string MUST contain a fixed redaction placeholder for the apiKey field
- AND the returned string MUST NOT contain `sk-super-secret-key`

#### Scenario: toString preserves non-sensitive fields
- GIVEN a `LlmConfig` with `baseUrl` set to `https://api.openai.com/v1`, `model` set to `gpt-4`, `timeout` set to `60`, `maxRetries` set to `3`
- WHEN `toString()` is called
- THEN the returned string MUST contain `https://api.openai.com/v1`
- AND it MUST contain `gpt-4`
- AND it MUST contain `60`
- AND it MUST contain `3`

### Requirement: LlmConfig constructor rejects without leaking API key
Previously: `LlmConfig` compact constructor threw `IllegalArgumentException` that might reference the invalid API key value.
The `LlmConfig` compact constructor MUST throw `IllegalArgumentException` for invalid inputs without including the `apiKey` value in the exception message.

#### Scenario: Blank API key exception excludes secret
- GIVEN a construction attempt with a blank `apiKey`
- WHEN the `IllegalArgumentException` is thrown
- THEN the exception message MUST NOT contain the provided blank value
- AND the message MUST describe the constraint violation without echoing the input value

### Requirement: SET LLM exception messages exclude secrets
Previously: `SetLlmSqlException` messages did not have explicit secret-exclusion guarantees.
`SetLlmSqlException` and any exception thrown by `DefaultLlmProviderRegistry` during config mutation MUST NOT include resolved secret values or Vault reference names in the exception message.

#### Scenario: Permission denied exception excludes config secrets
- GIVEN a `SET LLM` statement is rejected due to permission denial
- WHEN the exception message is inspected
- THEN it MUST NOT contain any API key value
- AND it MUST describe the denial reason using only non-sensitive identifiers such as session ID and permission action

## ADDED Requirements

### Requirement: LLM_CONFIG_CHANGED event metadata secret guard
`DefaultLlmProviderRegistry` MUST ensure that `LLM_CONFIG_CHANGED` event metadata never contains secret values. When publishing an event, the registry MUST validate that metadata values do not match the resolved API key for the affected provider.

#### Scenario: Event metadata does not contain API key after config change
- GIVEN a provider with API key `sk-real-key` is active
- AND a successful runtime config change publishes `LLM_CONFIG_CHANGED`
- WHEN the event metadata values are inspected
- THEN no metadata value MUST equal `sk-real-key`

#### Scenario: Event metadata does not contain vault reference after config change
- GIVEN a provider was constructed from `vault:openai_key` reference
- AND a successful runtime config change publishes `LLM_CONFIG_CHANGED`
- WHEN the event metadata values are inspected
- THEN no metadata value MUST equal `vault:openai_key`

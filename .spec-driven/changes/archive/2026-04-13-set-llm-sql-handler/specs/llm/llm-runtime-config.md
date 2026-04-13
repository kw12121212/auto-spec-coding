---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/LlmConfigSnapshot.java
    - src/main/java/org/specdriven/agent/agent/LlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/agent/DefaultLlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/agent/SetLlmSqlException.java
    - src/main/java/org/specdriven/agent/agent/SetLlmStatementParser.java
  tests:
    - src/test/java/org/specdriven/agent/agent/DefaultLlmProviderRegistryTest.java
    - src/test/java/org/specdriven/agent/agent/SetLlmStatementParserTest.java
---

# Runtime LLM Config

## ADDED Requirements

### Requirement: SET LLM updates supported non-sensitive runtime parameters
The system MUST support updating the active runtime LLM configuration for a session through a `SET LLM` SQL statement that carries supported non-sensitive parameter assignments.

#### Scenario: SET LLM updates later requests in the same session
- GIVEN a session currently resolves runtime snapshot `S1`
- AND a `SET LLM` statement assigns supported non-sensitive parameters such as provider, model, base URL, timeout, or retry-related fields
- WHEN the statement completes successfully for that session
- THEN later LLM requests started by that session MUST resolve a replacement snapshot reflecting those assigned values
- AND requests started by other sessions MUST continue resolving their own snapshots unchanged

#### Scenario: Missing parameter in SET LLM keeps prior effective value
- GIVEN a session currently resolves runtime snapshot `S1`
- WHEN a successful `SET LLM` statement assigns only a subset of supported parameters
- THEN the replacement snapshot for that session MUST preserve the prior effective value for every supported parameter not mentioned in the statement

### Requirement: SET LLM applies updates atomically
The system MUST apply each successful `SET LLM` statement as one atomic runtime snapshot replacement for the targeted scope.

#### Scenario: Successful statement installs one coherent replacement snapshot
- GIVEN a `SET LLM` statement assigns multiple supported parameters
- WHEN the statement succeeds
- THEN later requests MUST observe either the full pre-statement snapshot or the full post-statement snapshot
- AND no later request may observe a mixture of old and new parameter values from that statement

#### Scenario: Failed statement leaves prior snapshot active
- GIVEN a session currently resolves runtime snapshot `S1`
- WHEN a `SET LLM` statement fails validation or execution before completion
- THEN later requests in that session MUST continue resolving `S1`
- AND no partial update from the failed statement may become active

### Requirement: SET LLM rejects unsupported or invalid assignments
The system MUST reject `SET LLM` assignments that target unsupported keys or provide invalid values for supported non-sensitive runtime parameters.

#### Scenario: Unsupported key is rejected
- GIVEN a `SET LLM` statement includes a key that is outside the supported non-sensitive runtime LLM config contract
- WHEN the statement is evaluated
- THEN the statement MUST fail
- AND the active runtime snapshot for that scope MUST remain unchanged

#### Scenario: Invalid value is rejected
- GIVEN a `SET LLM` statement includes a supported key with an invalid value such as a blank provider name or a non-positive timeout
- WHEN the statement is evaluated
- THEN the statement MUST fail
- AND the active runtime snapshot for that scope MUST remain unchanged

### Requirement: SET LLM preserves in-flight request binding
Applying runtime LLM config changes through `SET LLM` MUST NOT change the snapshot already bound to an in-flight request.

#### Scenario: In-flight request continues with pre-update snapshot
- GIVEN an LLM request starts in a session using snapshot `S1`
- AND the same session later executes a successful `SET LLM` statement that installs snapshot `S2`
- WHEN the in-flight request continues to completion
- THEN it MUST continue using `S1`
- AND only later requests started after the statement completes MAY observe `S2`

### Requirement: SET LLM does not introduce secret governance behavior
The `SET LLM` behavior defined by this change MUST remain limited to non-sensitive runtime config fields.

#### Scenario: Secret-bearing governance remains outside this change
- GIVEN runtime LLM updates through `SET LLM` are enabled
- WHEN this change defines the SQL update contract
- THEN secret references, secret redaction, permission checks, and audit governance MUST remain outside this change
- AND those behaviors MUST be specified by later roadmap items instead of being implied here

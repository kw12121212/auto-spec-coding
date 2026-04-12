---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/LlmConfigSnapshot.java
    - src/main/java/org/specdriven/agent/agent/LlmProvider.java
    - src/main/java/org/specdriven/agent/agent/LlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/agent/DefaultLlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/agent/OpenAiClient.java
    - src/main/java/org/specdriven/agent/agent/ClaudeClient.java
    - src/main/java/org/specdriven/agent/agent/OpenAiProvider.java
    - src/main/java/org/specdriven/agent/agent/ClaudeProvider.java
    - src/main/java/org/specdriven/sdk/SdkAgent.java
    - src/main/java/org/specdriven/skill/executor/SkillServiceExecutor.java
  tests:
    - src/test/java/org/specdriven/agent/agent/LlmConfigTest.java
    - src/test/java/org/specdriven/agent/agent/DefaultLlmProviderRegistryTest.java
    - src/test/java/org/specdriven/agent/agent/OpenAiProviderTest.java
    - src/test/java/org/specdriven/agent/agent/ClaudeProviderTest.java
---

# Runtime LLM Config

## ADDED Requirements

### Requirement: Immutable runtime LLM config snapshot
The system MUST expose runtime LLM configuration as an immutable snapshot value containing the effective non-sensitive fields used for future LLM requests.

#### Scenario: Snapshot captures effective request settings
- GIVEN a runtime LLM config snapshot created for provider `openai`
- WHEN the snapshot is inspected
- THEN it MUST expose the effective non-sensitive request settings for that scope, including provider selection and request parameters such as model, base URL, timeout, temperature, or equivalent supported fields
- AND changing a later snapshot MUST NOT mutate the earlier snapshot instance

#### Scenario: Snapshot excludes secret governance behavior
- GIVEN runtime LLM config snapshots are enabled
- WHEN the system defines snapshot behavior in this change
- THEN the snapshot contract MUST cover only non-sensitive fields
- AND secret reference resolution, redaction, and permission governance MUST remain outside this change

### Requirement: Atomic replacement for future requests
The system MUST support atomically replacing the active runtime LLM config snapshot for a scope so that future requests observe the replacement as a single switch.

#### Scenario: Future requests observe replacement
- GIVEN scope `session-a` currently resolves to snapshot `S1`
- AND a later replacement installs snapshot `S2`
- WHEN a new LLM request starts after the replacement completes
- THEN that request MUST resolve `S2`
- AND it MUST NOT observe a mixture of fields from `S1` and `S2`

#### Scenario: Concurrent readers never observe partial update
- GIVEN multiple threads resolve the active snapshot while another thread replaces it
- WHEN reads occur during the replacement window
- THEN each read MUST return either the full pre-replacement snapshot or the full post-replacement snapshot
- AND no read may return a partially updated configuration

### Requirement: In-flight request snapshot binding
Each LLM request MUST bind to exactly one resolved runtime snapshot for the full lifetime of that request.

#### Scenario: In-flight request keeps original snapshot
- GIVEN an LLM request starts using snapshot `S1`
- AND the active snapshot is replaced with `S2` before the request completes
- WHEN the in-flight request continues
- THEN it MUST continue using `S1` until completion
- AND only later requests MAY observe `S2`

### Requirement: Session-scoped runtime isolation baseline
The system MUST support resolving runtime LLM config snapshots by scope so one session can observe a different active snapshot than another session.

#### Scenario: Session override does not affect other sessions
- GIVEN `session-a` resolves to snapshot `S1`
- AND `session-b` resolves to snapshot `S2`
- WHEN both sessions start new LLM requests
- THEN requests in `session-a` MUST use `S1`
- AND requests in `session-b` MUST use `S2`

#### Scenario: Missing scoped snapshot falls back to default
- GIVEN no session-specific snapshot exists for `session-c`
- AND a default runtime snapshot `S-default` is configured
- WHEN `session-c` starts a new LLM request
- THEN the request MUST use `S-default`

### Requirement: Runtime config changes do not alter provider protocol semantics
Introducing runtime snapshots MUST NOT change existing provider request/response protocol behavior beyond selecting the effective config values used for new requests.

#### Scenario: Existing provider behavior remains stable
- GIVEN a provider already supports request serialization, retries, streaming, and tool calls
- WHEN runtime snapshot support is enabled
- THEN those behaviors MUST remain the same for any given effective snapshot
- AND the only observable difference MUST be which snapshot a new request resolves before it starts

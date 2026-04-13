---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/DefaultLlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/agent/LlmProviderRegistry.java
  tests:
    - src/test/java/org/specdriven/agent/agent/DefaultLlmProviderRegistryTest.java
---

# Runtime LLM Config

## MODIFIED Requirements

### Requirement: Atomic replacement for future requests
The system MUST make runtime snapshot replacements observable to later requests started through an existing registry-managed client without requiring that client object to be recreated.

#### Scenario: Existing session client picks up a later session snapshot
- GIVEN a registry-managed client for `session-a` has already been created
- AND a first request from that client starts while `session-a` resolves snapshot `S1`
- AND a later successful runtime replacement makes `S2` active for later requests in `session-a`
- WHEN a second request starts from the same registry-managed client after the replacement completes
- THEN the first request MUST keep using `S1`
- AND the second request MUST resolve `S2`

### Requirement: In-flight request snapshot binding
Each LLM request started through a registry-managed client MUST stay bound to the resolved runtime snapshot and provider selected at request start until that request completes.

#### Scenario: Later requests may switch providers without recreating the client
- GIVEN a registry-managed client for `session-a` starts a request while `session-a` resolves snapshot `S1` with provider `openai`
- AND a later successful runtime replacement makes snapshot `S2` active for `session-a` with provider `claude`
- WHEN a later request starts from the same registry-managed client after the replacement completes
- THEN the later request MUST use provider `claude`
- AND the earlier in-flight request MUST continue using provider `openai` until completion

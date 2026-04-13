---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/LlmProvider.java
    - src/main/java/org/specdriven/agent/agent/OpenAiProvider.java
    - src/main/java/org/specdriven/agent/agent/ClaudeProvider.java
    - src/main/java/org/specdriven/agent/agent/OpenAiClient.java
    - src/main/java/org/specdriven/agent/agent/ClaudeClient.java
    - src/main/java/org/specdriven/agent/agent/DefaultLlmProviderRegistry.java
  tests:
    - src/test/java/org/specdriven/agent/agent/OpenAiProviderTest.java
    - src/test/java/org/specdriven/agent/agent/ClaudeProviderTest.java
    - src/test/java/org/specdriven/agent/agent/DefaultLlmProviderRegistryTest.java
---

# llm-provider.md

## ADDED Requirements

### Requirement: Snapshot-aware provider client creation
The system MUST allow a provider to create a client from a supplied runtime snapshot so future requests use the snapshot's effective non-sensitive request settings without changing provider-owned authentication configuration.

#### Scenario: OpenAI snapshot client uses snapshot settings and provider authentication
- GIVEN an `OpenAiProvider` configured with registered authentication data
- AND a runtime snapshot for provider `openai` supplies effective base URL, model, timeout, and retry values
- WHEN `createClient(snapshot)` is called
- THEN the returned client MUST use the snapshot's effective base URL, model, timeout, and retry values for requests started through that client
- AND it MUST keep using the provider's configured authentication material

#### Scenario: Claude snapshot client uses snapshot settings and provider authentication
- GIVEN a `ClaudeProvider` configured with registered authentication data
- AND a runtime snapshot for provider `claude` supplies effective base URL, model, timeout, and retry values
- WHEN `createClient(snapshot)` is called
- THEN the returned client MUST use the snapshot's effective base URL, model, timeout, and retry values for requests started through that client
- AND it MUST keep using the provider's configured authentication material

### Requirement: Registry-managed clients use snapshot-aware provider resolution
The system MUST let a registry-managed client resolve the effective provider from the active runtime snapshot at the start of each request.

#### Scenario: Later request uses the provider selected by the replacement snapshot
- GIVEN a registry-managed client has already been created for a session
- AND the session currently resolves a snapshot whose provider is `openai`
- AND a later successful runtime replacement makes a snapshot with provider `claude` active for later requests in that session
- WHEN a new request starts after the replacement completes
- THEN that new request MUST be sent using a client created from the `claude` provider

#### Scenario: Streaming request keeps the provider chosen at request start
- GIVEN a registry-managed client starts a streaming request after resolving snapshot `S1`
- AND a later successful runtime replacement makes `S2` active before the stream completes
- WHEN the in-flight stream continues
- THEN the stream MUST continue using the provider client created from `S1`
- AND only later requests MAY use a provider client created from `S2`

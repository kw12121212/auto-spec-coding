---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/DefaultLlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/agent/LlmConfig.java
    - src/main/java/org/specdriven/agent/agent/LlmConfigSnapshot.java
    - src/main/java/org/specdriven/agent/agent/LlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/llm/LealoneRuntimeLlmConfigStore.java
    - src/main/java/org/specdriven/agent/llm/RuntimeLlmConfigStore.java
    - src/main/java/org/specdriven/agent/vault/SecretVault.java
    - src/main/java/org/specdriven/agent/vault/VaultResolver.java
  tests:
    - src/test/java/org/specdriven/agent/agent/DefaultLlmProviderRegistryTest.java
    - src/test/java/org/specdriven/agent/vault/VaultResolverTest.java
---

# Runtime LLM Config

## ADDED Requirements

### Requirement: LLM provider config resolves Vault-backed authentication references
The system MUST support constructing LLM providers from provider config values whose sensitive authentication fields use `vault:<key>` references.

#### Scenario: Provider API key resolves from Vault before provider creation
- GIVEN LLM provider config for provider `openai` with `apiKey` set to `vault:openai_key`
- AND a `SecretVault` where `openai_key` resolves to `sk-real-key`
- WHEN the provider registry is constructed through the Vault-aware LLM config path
- THEN the registered provider's effective authentication config MUST use `sk-real-key`
- AND non-sensitive provider config fields such as provider type, base URL, model, timeout, and retry values MUST remain otherwise unchanged

#### Scenario: Plain provider API key remains supported
- GIVEN LLM provider config for provider `openai` with `apiKey` set to `sk-local-test`
- WHEN the provider registry is constructed through the Vault-aware LLM config path
- THEN the registered provider's effective authentication config MUST use `sk-local-test`
- AND the provider MUST be registered successfully

#### Scenario: Missing Vault key fails provider registry construction
- GIVEN LLM provider config for provider `openai` with `apiKey` set to `vault:missing_key`
- AND a `SecretVault` with no `missing_key` entry
- WHEN the provider registry is constructed through the Vault-aware LLM config path
- THEN construction MUST fail with a Vault-specific error
- AND the provider MUST NOT become usable with the unresolved literal `vault:missing_key`

### Requirement: Runtime snapshots remain non-sensitive after Vault resolution
Resolving Vault-backed provider authentication material MUST NOT add secret values or Vault reference names to runtime LLM snapshots.

#### Scenario: Snapshot excludes resolved secret
- GIVEN a provider was constructed from `apiKey` set to `vault:openai_key`
- AND the Vault reference resolved to `sk-real-key`
- WHEN the system exposes the provider's runtime `LlmConfigSnapshot`
- THEN the snapshot MUST expose only non-sensitive runtime fields such as provider name, base URL, model, timeout, and retry values
- AND the snapshot MUST NOT expose `sk-real-key`
- AND the snapshot MUST NOT expose `vault:openai_key`

#### Scenario: Persisted runtime config history excludes resolved secret
- GIVEN a provider was constructed from Vault-backed authentication material
- WHEN the system persists a default runtime LLM config snapshot
- THEN the persisted runtime config record MUST contain only non-sensitive snapshot fields
- AND it MUST NOT contain the resolved secret value
- AND it MUST NOT contain the Vault reference name

### Requirement: Vault resolution does not expand SET LLM mutation surface
Vault-backed LLM provider authentication integration MUST NOT make `SET LLM` a secret mutation or secret reference update surface.

#### Scenario: SET LLM remains limited to non-sensitive runtime parameters
- GIVEN Vault-backed provider authentication resolution is enabled
- WHEN a `SET LLM` statement is evaluated
- THEN the supported assignment keys MUST remain limited to non-sensitive runtime parameters
- AND authentication secrets or Vault reference assignments MUST remain outside the `SET LLM` contract for this change

---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/vault/SecretVault.java
    - src/main/java/org/specdriven/agent/vault/VaultResolver.java
    - src/main/java/org/specdriven/agent/agent/DefaultLlmProviderRegistry.java
  tests:
    - src/test/java/org/specdriven/agent/vault/VaultResolverTest.java
    - src/test/java/org/specdriven/agent/agent/DefaultLlmProviderRegistryTest.java
---

# secret-vault.md

## MODIFIED Requirements

### Requirement: VaultResolver
The system MUST allow LLM provider configuration assembly to reuse `VaultResolver` semantics for `vault:<key>` references in sensitive provider authentication fields.

#### Scenario: Resolve LLM provider API key reference
- GIVEN an LLM provider config map with entry `apiKey` set to `vault:openai_key`
- AND a `SecretVault` where `get("openai_key")` returns `sk-real-key`
- WHEN the LLM provider config is resolved through VaultResolver before provider creation
- THEN the resolved provider config MUST contain `apiKey` set to `sk-real-key`

#### Scenario: Preserve non-vault LLM provider config values
- GIVEN an LLM provider config map with `apiKey` set to `vault:openai_key`
- AND `model` set to `gpt-4`
- AND `baseUrl` set to `https://api.openai.com/v1`
- WHEN the LLM provider config is resolved through VaultResolver before provider creation
- THEN `apiKey` MUST be resolved from Vault
- AND `model` MUST remain `gpt-4`
- AND `baseUrl` MUST remain `https://api.openai.com/v1`

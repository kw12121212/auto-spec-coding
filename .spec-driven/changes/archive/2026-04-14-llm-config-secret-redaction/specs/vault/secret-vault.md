---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/vault/VaultResolver.java
  tests:
    - src/test/java/org/specdriven/agent/vault/VaultResolverTest.java
---

## MODIFIED Requirements

### Requirement: VaultResolver exception messages exclude resolved secrets
Previously: `VaultResolver` threw `VaultException` for missing vault keys without explicitly guaranteeing that the exception message excludes other resolved secret values.
When `VaultResolver` throws `VaultException` during resolution, the exception message MUST NOT include any successfully resolved secret values from the same resolution batch.

#### Scenario: Partial resolution failure does not leak other resolved values
- GIVEN a config map with `apiKey` set to `vault:existing_key` (resolves to `sk-real-key`) and `secret2` set to `vault:missing_key` (does not exist)
- WHEN `VaultResolver.resolve(config, vault)` is called
- AND the `VaultException` is thrown for the missing key
- THEN the exception message MUST NOT contain `sk-real-key`
- AND the exception message MUST identify the missing key name

# config-loader delta — vault-config-integration

## ADDED Requirements

### Requirement: ConfigLoader vault-aware loading

The system MUST provide vault-aware config loading methods that resolve `vault:` references in config values.

#### Scenario: Load with vault resolution
- GIVEN a YAML config file containing `llm.apiKey: vault:openai_key`
- AND a `SecretVault` where `get("openai_key")` returns "sk-real-key"
- WHEN `ConfigLoader.loadWithVault(path, vault)` is called
- THEN the returned map MUST contain `"llm.apiKey" → "sk-real-key"`

#### Scenario: Load with vault from classpath
- GIVEN a classpath YAML resource containing `llm.apiKey: vault:openai_key`
- AND a `SecretVault` where `get("openai_key")` returns "sk-real-key"
- WHEN `ConfigLoader.loadWithVaultClasspath(resource, vault)` is called
- THEN the returned map MUST contain `"llm.apiKey" → "sk-real-key"`

#### Scenario: Mixed vault and plain values
- GIVEN a YAML config with `llm.apiKey: vault:openai_key` and `llm.model: gpt-4`
- AND a `SecretVault` where `get("openai_key")` returns "sk-real-key"
- WHEN `ConfigLoader.loadWithVault(path, vault)` is called
- THEN `"llm.apiKey"` MUST be resolved to "sk-real-key"
- AND `"llm.model"` MUST remain "gpt-4"

#### Scenario: Missing vault key error
- GIVEN a YAML config with `llm.apiKey: vault:nonexistent`
- AND a `SecretVault` with no entry for "nonexistent"
- WHEN `ConfigLoader.loadWithVault(path, vault)` is called
- THEN it MUST throw `VaultException`

#### Scenario: No vault references passthrough
- GIVEN a YAML config with no `vault:` prefixed values
- WHEN `ConfigLoader.loadWithVault(path, vault)` is called
- THEN the returned map MUST be identical to `ConfigLoader.load(path).asMap()`

#### Scenario: Env-var substitution before vault resolution
- GIVEN a YAML config with `llm.apiKey: vault:${KEY_NAME}` where env var `KEY_NAME=openai_key`
- AND a `SecretVault` where `get("openai_key")` returns "sk-real-key"
- WHEN `ConfigLoader.loadWithVault(path, vault)` is called with env-var substitution enabled
- THEN `"llm.apiKey"` MUST be resolved to "sk-real-key"

### Requirement: VaultFactory convenience utility

The system MUST provide a `VaultFactory` utility for creating LealoneVault instances with sensible defaults.

#### Scenario: Create vault with defaults
- GIVEN environment variable `SPEC_DRIVEN_MASTER_KEY` is set
- WHEN `VaultFactory.create(eventBus)` is called
- THEN it MUST return a usable `LealoneVault` instance

#### Scenario: Create vault with custom JDBC URL
- GIVEN a custom JDBC URL "jdbc:lealone:./my-vault"
- WHEN `VaultFactory.create(eventBus, "jdbc:lealone:./my-vault")` is called
- THEN it MUST return a `LealoneVault` using the provided JDBC URL

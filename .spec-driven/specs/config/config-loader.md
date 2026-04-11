---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/config/Config.java
    - src/main/java/org/specdriven/agent/config/ConfigException.java
    - src/main/java/org/specdriven/agent/config/ConfigLoader.java
    - src/main/java/org/specdriven/agent/vault/VaultFactory.java
    - src/main/java/org/specdriven/agent/vault/VaultResolver.java
  tests:
    - src/test/java/org/specdriven/agent/config/ConfigLoaderTest.java
    - src/test/java/org/specdriven/agent/config/ConfigLoaderVaultIntegrationTest.java
    - src/test/java/org/specdriven/agent/vault/VaultFactoryTest.java
---

# Config Loader Spec

## ADDED Requirements

### Requirement: ConfigLoader entry point

- MUST provide `load(Path)` to load YAML from filesystem
- MUST provide `loadClasspath(String)` to load YAML from classpath
- MUST throw `ConfigException` if the source file does not exist or cannot be read
- MUST throw `ConfigException` if the YAML content is malformed

### Requirement: Config typed access

- MUST provide `getString(String key)` returning `String` or throwing `ConfigException` for missing keys
- MUST provide `getString(String key, String defaultValue)` returning default when key is absent
- MUST provide `getInt(String key, int defaultValue)` parsing string values to int
- MUST provide `getBoolean(String key, boolean defaultValue)` parsing string values to boolean
- MUST support dot-notation keys for nested access (e.g., `"llm.provider"` resolves `{llm: {provider: x}}`)
- MUST be immutable — no setter methods

### Requirement: Config section access

- MUST provide `getSection(String prefix)` returning a `Config` scoped to the nested subtree
- Keys within the sub-config MUST be relative to the section prefix

### Requirement: Config flattening

- MUST provide `asMap()` returning `Map<String, String>` with all nested keys flattened to dot-notation
- The returned map MUST be compatible with `Agent.init(Map<String, String>)`

### Requirement: Environment variable substitution

- SHOULD resolve `${VAR_NAME}` patterns in string values to the corresponding system environment variable
- MUST leave the pattern unresolved (as-is) if the environment variable is not defined
- MUST be opt-in via a parameter on the load method

### Requirement: ConfigException

- MUST be a RuntimeException subclass
- MUST carry a descriptive message indicating the config source and nature of the error

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

# secret-vault.md

## ADDED Requirements

### Requirement: SecretVault Interface
The system MUST define a `SecretVault` interface providing encrypted secret storage and retrieval.

#### Scenario: Get existing secret
- GIVEN a `SecretVault` with key "openai_key" set to plaintext "sk-abc123"
- WHEN `get("openai_key")` is called
- THEN it MUST return the decrypted plaintext "sk-abc123"

#### Scenario: Get missing secret
- GIVEN a `SecretVault` with no entry for "missing_key"
- WHEN `get("missing_key")` is called
- THEN it MUST throw `VaultException`

#### Scenario: Set new secret
- GIVEN a `SecretVault` instance
- WHEN `set("new_key", "secret_value", "API key for service X")` is called
- THEN subsequent `get("new_key")` MUST return "secret_value"
- AND `exists("new_key")` MUST return true

#### Scenario: Overwrite existing secret (rotation)
- GIVEN a `SecretVault` with key "api_key" set to "old_value"
- WHEN `set("api_key", "new_value", "rotated key")` is called
- THEN subsequent `get("api_key")` MUST return "new_value"

#### Scenario: Delete secret
- GIVEN a `SecretVault` with key "temp_key" set
- WHEN `delete("temp_key")` is called
- THEN `exists("temp_key")` MUST return false
- AND `get("temp_key")` MUST throw `VaultException`

#### Scenario: Delete missing secret
- GIVEN a `SecretVault` with no entry for "ghost"
- WHEN `delete("ghost")` is called
- THEN it MUST complete without error (idempotent)

#### Scenario: List entries
- GIVEN a `SecretVault` with keys "key_a" and "key_b" set
- WHEN `list()` is called
- THEN it MUST return a list of `VaultEntry` with exactly 2 entries
- AND each entry MUST have a non-null `key`, `createdAt`, and `description`
- AND the list MUST NOT contain decrypted values

#### Scenario: Check existence
- GIVEN a `SecretVault` with key "exists_key" set
- WHEN `exists("exists_key")` is called
- THEN it MUST return true
- WHEN `exists("no_such_key")` is called
- THEN it MUST return false

### Requirement: VaultEntry Record
The system MUST define an immutable `VaultEntry` record carrying secret metadata.

#### Scenario: Entry fields
- GIVEN a `VaultEntry` created with key "my_key", createdAt = some instant, description = "test key"
- THEN `key()` MUST return "my_key"
- AND `createdAt()` MUST return the provided instant
- AND `description()` MUST return "test key"

### Requirement: VaultResolver
The system MUST provide a `VaultResolver` utility that resolves `vault:key_name` references in config maps.

#### Scenario: Resolve vault reference
- GIVEN a config map with entry `"llm.apiKey" → "vault:openai_key"` and a `SecretVault` where `get("openai_key")` returns "sk-real-key"
- WHEN `VaultResolver.resolve(config, vault)` is called
- THEN the returned map MUST contain `"llm.apiKey" → "sk-real-key"`

#### Scenario: Pass through non-vault values
- GIVEN a config map with entry `"llm.model" → "gpt-4"` and entry `"llm.apiKey" → "vault:openai_key"`
- WHEN `VaultResolver.resolve(config, vault)` is called
- THEN `"llm.model"` MUST remain "gpt-4" (unchanged)
- AND `"llm.apiKey"` MUST be resolved to the decrypted value

#### Scenario: Missing vault key
- GIVEN a config map with entry `"llm.apiKey" → "vault:nonexistent"` and a `SecretVault` with no such key
- WHEN `VaultResolver.resolve(config, vault)` is called
- THEN it MUST throw `VaultException`

#### Scenario: Empty config
- GIVEN an empty config map
- WHEN `VaultResolver.resolve(config, vault)` is called
- THEN it MUST return an empty map without error

#### Scenario: No vault references
- GIVEN a config map with no `vault:` prefixed values
- WHEN `VaultResolver.resolve(config, vault)` is called
- THEN it MUST return a map with identical entries to the input

### Requirement: VaultException
The system MUST define a `VaultException` extending `RuntimeException` for vault-specific errors.

#### Scenario: Message-only constructor
- GIVEN `new VaultException("key not found")`
- THEN `getMessage()` MUST return "key not found"
- AND `getCause()` MUST be null

#### Scenario: Message with cause
- GIVEN `new VaultException("decryption failed", someException)`
- THEN `getMessage()` MUST return "decryption failed"
- AND `getCause()` MUST be the provided exception

### Requirement: VaultMasterKey Utility
The system MUST provide a `VaultMasterKey` utility for obtaining the master encryption key.

#### Scenario: From environment variable
- GIVEN the environment variable `SPEC_DRIVEN_MASTER_KEY` is set to "my-production-key"
- WHEN `VaultMasterKey.get()` is called
- THEN it MUST return "my-production-key"

#### Scenario: Default fallback for development
- GIVEN the environment variable `SPEC_DRIVEN_MASTER_KEY` is not set
- WHEN `VaultMasterKey.get()` is called
- THEN it MUST return a fixed default key
- AND a warning SHOULD be logged indicating the default key is in use

#### Scenario: Is default check
- GIVEN `VaultMasterKey` is using the default key
- WHEN `VaultMasterKey.isDefault()` is called
- THEN it MUST return true
- GIVEN `VaultMasterKey` is using a custom key from the env var
- WHEN `VaultMasterKey.isDefault()` is called
- THEN it MUST return false

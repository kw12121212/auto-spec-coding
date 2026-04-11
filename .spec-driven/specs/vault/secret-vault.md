---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/event/EventType.java
    - src/main/java/org/specdriven/agent/vault/LealoneVault.java
    - src/main/java/org/specdriven/agent/vault/SecretVault.java
    - src/main/java/org/specdriven/agent/vault/VaultEntry.java
    - src/main/java/org/specdriven/agent/vault/VaultException.java
    - src/main/java/org/specdriven/agent/vault/VaultFactory.java
    - src/main/java/org/specdriven/agent/vault/VaultMasterKey.java
    - src/main/java/org/specdriven/agent/vault/VaultResolver.java
  tests:
    - src/test/java/org/specdriven/agent/vault/LealoneVaultTest.java
    - src/test/java/org/specdriven/agent/vault/SecretVaultTest.java
    - src/test/java/org/specdriven/agent/vault/VaultEntryTest.java
    - src/test/java/org/specdriven/agent/vault/VaultExceptionTest.java
    - src/test/java/org/specdriven/agent/vault/VaultFactoryTest.java
    - src/test/java/org/specdriven/agent/vault/VaultMasterKeyTest.java
    - src/test/java/org/specdriven/agent/vault/VaultResolverTest.java
---

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

### Requirement: LealoneVault Implementation
The system MUST provide a `LealoneVault` class implementing `SecretVault` with AES-256-GCM encryption and Lealone DB persistence.

#### Scenario: Encrypt and retrieve secret
- GIVEN a `LealoneVault` initialized with a valid master key
- WHEN `set("openai_key", "sk-abc123", "OpenAI API key")` is called
- AND `get("openai_key")` is called
- THEN it MUST return "sk-abc123"

#### Scenario: Secret stored as ciphertext in DB
- GIVEN a `LealoneVault` where `set("api_key", "secret_val", "test")` was called
- WHEN the `vault_secrets` table is queried directly
- THEN the stored value MUST NOT be "secret_val" (must be encrypted)

#### Scenario: No master key decryption failure
- GIVEN a `LealoneVault` initialized with master key "key-A"
- AND `set("test", "value", "desc")` is called
- WHEN a new `LealoneVault` is created with master key "key-B" pointing to the same DB
- AND `get("test")` is called
- THEN it MUST throw `VaultException`

#### Scenario: Idempotent table creation
- GIVEN a `LealoneVault` instance
- WHEN a second `LealoneVault` is created with the same JDBC URL
- THEN no error MUST occur (tables use IF NOT EXISTS)

### Requirement: Vault Audit Log
The system MUST log all vault CRUD operations to a `vault_audit_log` table.

#### Scenario: Set operation logged
- GIVEN a `LealoneVault` instance
- WHEN `set("key1", "val", "desc")` is called
- THEN the `vault_audit_log` table MUST contain a row with operation "SET" and vault_key "key1"

#### Scenario: Delete operation logged
- GIVEN a `LealoneVault` with key "key1" set
- WHEN `delete("key1")` is called
- THEN the `vault_audit_log` table MUST contain a row with operation "DELETE" and vault_key "key1"

#### Scenario: Get operation NOT logged
- GIVEN a `LealoneVault` with key "key1" set
- WHEN `get("key1")` is called
- THEN the `vault_audit_log` table MUST NOT contain a row for the get operation
- (Read operations are not audited to avoid noise; only mutations are)

### Requirement: Vault EventType Integration
The system MUST publish vault events via EventBus for integration with the existing audit infrastructure.

#### Scenario: Set publishes event
- GIVEN a `LealoneVault` with an EventBus
- WHEN `set("key1", "val", "desc")` is called
- THEN an event with type `VAULT_SECRET_CREATED` MUST be published

#### Scenario: Delete publishes event
- GIVEN a `LealoneVault` with key "key1" set
- WHEN `delete("key1")` is called
- THEN an event with type `VAULT_SECRET_DELETED` MUST be published

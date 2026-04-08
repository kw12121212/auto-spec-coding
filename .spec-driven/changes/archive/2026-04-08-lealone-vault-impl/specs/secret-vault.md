# secret-vault.md

## ADDED Requirements

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

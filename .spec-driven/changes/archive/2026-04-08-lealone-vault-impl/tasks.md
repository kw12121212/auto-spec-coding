# Tasks: lealone-vault-impl

## Implementation

- [x] Add `VAULT_SECRET_CREATED` and `VAULT_SECRET_DELETED` to `EventType` enum in `src/main/java/org/specdriven/agent/event/EventType.java`
- [x] Create `LealoneVault` class in `src/main/java/org/specdriven/agent/vault/LealoneVault.java`:
  - Constructor: `(EventBus eventBus, String jdbcUrl)` — calls `initTables()` and derives AES key from `VaultMasterKey.get()`
  - `initTables()`: CREATE TABLE IF NOT EXISTS for `vault_secrets` (key VARCHAR(255) PK, encrypted_value CLOB, iv VARCHAR(32), description CLOB, created_at BIGINT, updated_at BIGINT) and `vault_audit_log` (id BIGINT AUTO_INCREMENT PK, operation VARCHAR(10), vault_key VARCHAR(255), event_ts BIGINT)
  - `get(key)`: SELECT encrypted_value, iv → decrypt → return plaintext; throw VaultException if not found or decryption fails
  - `set(key, plaintext, description)`: encrypt plaintext with random IV → MERGE INTO vault_secrets; insert audit row; publish EventBus event
  - `delete(key)`: DELETE FROM vault_secrets WHERE key=?; insert audit row; publish EventBus event; idempotent (no error if missing)
  - `list()`: SELECT key, description, created_at → return List<VaultEntry> (no decrypted values)
  - `exists(key)`: SELECT COUNT(*) FROM vault_secrets WHERE key=?
  - Private helper: `encrypt(byte[] plaintext)` / `decrypt(byte[] ciphertext, byte[] iv)` using AES/GCM/NoPadding
  - Private helper: `deriveAesKey(String masterKey)` → SHA-256 hash → SecretKeySpec

## Testing

- [x] Lint: run `mvn compile -pl . -q` and verify zero compilation errors
- [x] Unit tests: run `mvn test -pl . -Dtest="org.specdriven.agent.vault.*Test" -q` and verify all tests pass
- [x] Create `LealoneVaultTest` in `src/test/java/org/specdriven/agent/vault/LealoneVaultTest.java`:
  - `setUp()`: unique in-memory DB per test (`jdbc:lealone:embed:test_vault_...?PERSISTENT=false`), `VaultMasterKey.setEnvSource(() -> "test-master-key")`
  - `tearDown()`: `VaultMasterKey.reset()`
  - Test `get` returns decrypted value after `set`
  - Test `get` throws VaultException for missing key
  - Test `set` overwrites existing key (rotation)
  - Test `delete` removes key, subsequent `get` throws
  - Test `delete` is idempotent for missing key
  - Test `list` returns entries without decrypted values
  - Test `exists` returns true/false correctly
  - Test encrypted value in DB is not plaintext
  - Test wrong master key fails to decrypt
  - Test audit log entries are created on set/delete
  - Test EventBus events are published on set/delete
  - Test table creation is idempotent (two instances, same URL)

## Verification

- [x] Verify all tasks marked complete
- [x] Verify `mvn test -pl . -Dtest="org.specdriven.agent.vault.*Test"` passes with zero failures
- [x] Verify `LealoneVault` implements `SecretVault` interface
- [x] Verify delta spec `secret-vault.md` reflects what was built

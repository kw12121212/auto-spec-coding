# Tasks: secret-vault-interface

## Implementation

- [x] Create `VaultException` in `org.specdriven.agent.vault` — extends RuntimeException with message and optional cause
- [x] Create `VaultEntry` record in `org.specdriven.agent.vault` — fields: `key` (String), `createdAt` (Instant), `description` (String)
- [x] Create `SecretVault` interface in `org.specdriven.agent.vault` — methods: `get`, `set`, `delete`, `list`, `exists`
- [x] Create `VaultResolver` utility in `org.specdriven.agent.vault` — static `resolve(Map<String, String>, SecretVault)` that replaces `vault:key_name` values with decrypted plaintext
- [x] Create `VaultMasterKey` utility in `org.specdriven.agent.vault` — reads `SPEC_DRIVEN_MASTER_KEY` env var, falls back to fixed dev default, logs warning when using default

## Testing

- [x] `mvn compile` — build validation: verify project compiles with all new classes
- [x] `mvn test -pl . -Dtest=VaultEntryTest` — unit tests for VaultEntry record construction and accessors
- [x] `mvn test -pl . -Dtest=VaultExceptionTest` — unit tests for VaultException constructors (message-only, message+cause)
- [x] `mvn test -pl . -Dtest=SecretVaultTest` — unit tests for SecretVault contract using in-memory stub (get, set, delete, list, exists, overwrite, missing key)
- [x] `mvn test -pl . -Dtest=VaultResolverTest` — unit tests for vault: reference resolution, pass-through, missing key handling
- [x] `mvn test -pl . -Dtest=VaultMasterKeyTest` — unit tests for master key resolution (env var, default fallback, isDefault)
- [x] `mvn test` — full test suite passes (no regressions)

## Verification

- [x] Verify `SecretVault` interface matches proposal (get, set, delete, list, exists)
- [x] Verify `VaultResolver` handles `vault:key_name` pattern and passes through other values unchanged
- [x] Verify development default key is used when env var not set, with warning logged
- [x] Verify no changes to existing `Config`, `ConfigLoader`, or `LlmConfig` classes

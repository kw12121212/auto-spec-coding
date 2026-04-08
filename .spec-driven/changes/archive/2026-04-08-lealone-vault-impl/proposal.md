# lealone-vault-impl

## What

Implement `LealoneVault`, a `SecretVault` backed by Lealone DB with AES-256-GCM encryption, master key unlock via `SPEC_DRIVEN_MASTER_KEY` environment variable, and audit logging for all vault operations.

## Why

The `SecretVault` interface, `VaultEntry`, `VaultException`, `VaultMasterKey`, and `VaultResolver` are defined (M18 `secret-vault-interface` complete), but there is no concrete implementation. LLM providers and other modules need actual encrypted secret storage — right now the interface has no backing store. This change delivers the persistence and encryption layer so vault references (`vault:key_name`) can resolve to real decrypted values at runtime.

## Scope

- `LealoneVault` class implementing `SecretVault` with AES-256-GCM encryption/decryption
- Lealone DB table for encrypted secret storage (`vault_secrets`)
- Lealone DB table for vault audit log (`vault_audit_log`)
- Master key derived from `VaultMasterKey.get()` used for AES key material
- Proper nonce/IV generation (12-byte random per encryption) to avoid nonce reuse
- Audit events published via `EventBus` for vault CRUD operations
- Add `VAULT_SECRET_CREATED`, `VAULT_SECRET_DELETED` to `EventType` enum
- Unit tests using in-memory Lealone (`?PERSISTENT=false`) following existing store test patterns

## Unchanged Behavior

- `SecretVault` interface — no changes, only a new implementation
- `VaultEntry`, `VaultException`, `VaultMasterKey`, `VaultResolver` — unchanged
- `VaultResolver` continues to work against any `SecretVault` implementation
- Existing store implementations (TaskStore, TeamStore, CronStore) — no changes
- `EventType` enum gains new values but existing values and behavior unchanged

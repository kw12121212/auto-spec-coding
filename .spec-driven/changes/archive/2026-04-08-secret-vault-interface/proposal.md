# secret-vault-interface

## What

Define the `SecretVault` interface and `VaultResolver` utility for encrypted secret management. `SecretVault` provides CRUD operations for secrets encrypted with AES-256-GCM, unlocked by a single `SPEC_DRIVEN_MASTER_KEY` environment variable. `VaultResolver` integrates into the config loading chain to auto-resolve `vault:key_name` references to plaintext values.

This is the first of three changes in M18 (密钥保险库). It defines the interface and resolver; the Lealone DB implementation and config integration follow in `lealone-vault-impl` and `vault-config-integration`.

## Why

API keys and tokens are currently stored as plaintext in config files or environment variables. Before the system can be used in production, credentials need proper encryption at rest. The vault provides a single master key pattern: one `SPEC_DRIVEN_MASTER_KEY` environment variable unlocks all secrets stored encrypted in Lealone DB.

A fixed default master key is provided for development environments so developers don't need to set the env var locally. Production deployments MUST set `SPEC_DRIVEN_MASTER_KEY` explicitly.

## Scope

- `SecretVault` interface: `get`, `set`, `delete`, `list`, `exists` operations
- `VaultEntry` record: encrypted value + metadata (creation timestamp, description)
- `VaultResolver` utility: resolves `vault:key_name` patterns in config maps to plaintext
- `VaultException` for vault-specific errors
- Unit tests against an in-memory implementation of the interface

## Unchanged Behavior

- Existing `Config` and `ConfigLoader` APIs remain unchanged
- `LlmConfig` continues to consume plaintext strings only — it does not know about the vault
- Environment variable substitution (`${VAR_NAME}`) in config continues to work independently

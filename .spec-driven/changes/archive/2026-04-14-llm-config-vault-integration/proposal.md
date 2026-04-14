# llm-config-vault-integration

## What

- Integrate Vault-backed secret references into LLM provider configuration so authentication material can be loaded from `SecretVault` / `VaultResolver` before providers are created.
- Keep runtime LLM snapshots and persisted runtime config limited to non-sensitive fields while allowing provider-owned authentication material to originate from Vault references.
- Add behavior coverage for successful Vault reference resolution, missing secret failures, and pass-through of non-secret provider fields.

## Why

- M28 completed dynamic non-sensitive runtime LLM configuration and explicitly left secret reference resolution, permission governance, redaction, and audit behavior to later roadmap items.
- M33 should start by establishing the Vault boundary for LLM credentials; later permission, redaction, and audit changes depend on knowing where sensitive provider config is resolved.
- Without this change, a provider config value such as `apiKey: vault:openai_key` can remain an unresolved literal unless the caller happened to use a separate vault-aware config loading path.

## Scope

- In scope:
- Resolve LLM provider authentication fields that use `vault:<key>` references before constructing provider `LlmConfig` instances.
- Ensure non-vault provider config values continue to pass through unchanged.
- Fail clearly when an LLM provider config references a missing Vault key.
- Preserve `LlmConfigSnapshot` and runtime config persistence as non-sensitive contracts that exclude API keys and resolved secret values.
- Add unit tests for LLM registry/provider configuration with Vault references.
- Out of scope:
- Permission checks for `SET LLM` or other runtime config mutation entry points.
- Secret redaction in logs, events, exceptions, or persisted audit records.
- LLM config change audit records.
- New Vault backends, Vault rotation behavior, or changes to `SecretVault` storage semantics.
- Adding secret fields to runtime snapshots, `SET LLM`, or runtime config version history.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Runtime LLM snapshot replacement, session isolation, provider switching, and in-flight request binding semantics remain unchanged.
- Existing `ConfigLoader.loadWithVault(...)` behavior remains available and unchanged.
- Provider request/response wire behavior remains unchanged apart from using resolved authentication material when a Vault reference is supplied.
- Plaintext `apiKey` config values continue to work for existing local and test configurations.

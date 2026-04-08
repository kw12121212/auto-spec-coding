# vault-config-integration

## What

Integrate VaultResolver into the config loading pipeline so that `vault:key_name` references in YAML config files are automatically resolved to decrypted plaintext values before being consumed by modules like LlmConfig.

## Why

M18's vault infrastructure (SecretVault interface, LealoneVault implementation, VaultResolver utility) is complete but not wired into the config loading chain. Without this integration, no module can transparently use vault-backed secrets — the vault exists but is unreachable from normal config consumption paths. This change closes the loop: config values like `llm.apiKey: vault:openai_key` resolve to real decrypted keys at load time.

## Scope

- Add vault-aware loading methods to ConfigLoader that chain: `load YAML → flatten to map → VaultResolver.resolve()`
- Ensure vault resolution happens after env-var substitution (both transforms apply, env-var first)
- Config values without `vault:` prefix pass through unchanged
- Error clearly when a `vault:` reference points to a non-existent vault key
- Unit tests for the integration path

## Unchanged Behavior

- ConfigLoader.load() and ConfigLoader.loadClasspath() without vault parameter MUST behave identically to current behavior (no vault resolution)
- Config.asMap() output format is unchanged
- VaultResolver standalone behavior is unchanged
- LealoneVault implementation is unchanged
- AgentContext interface is unchanged

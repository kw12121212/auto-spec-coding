# Design: llm-config-vault-integration

## Approach

Add a narrow Vault-aware LLM registry construction path that resolves provider config maps before `LlmConfig.fromMap(...)` is called. The existing registry construction path should continue to work for callers that already pass fully resolved config.

Use the existing `VaultResolver` behavior for `vault:<key>` references rather than creating a second reference parser. Resolution should apply only to the provider config values being converted into `LlmConfig`; the resulting `LlmConfig` may contain resolved authentication material because provider clients need it to authenticate, but `LlmConfigSnapshot` and `RuntimeLlmConfigStore` must continue to omit it.

Keep failures explicit. If a provider config contains `apiKey: vault:missing_key`, registry construction should fail before registering that provider so callers do not get a provider that later authenticates with an unresolved literal.

## Key Decisions

- Resolve Vault references at provider config assembly time, before provider factories receive `LlmConfig`.
- Keep runtime snapshots non-sensitive and do not add `apiKey`, Vault key names, or resolved secret values to snapshot or history records.
- Reuse `VaultResolver` as the single `vault:` interpretation rule.
- Preserve existing `fromConfig(...)` overloads for already-resolved config and add a Vault-aware path instead of changing all callers.
- Limit this change to Vault integration. Permission guard, redaction, and change audit remain separate M33 planned changes.

## Alternatives Considered

- Require every caller to use `ConfigLoader.loadWithVault(...)` before constructing the registry. Rejected because M33 is specifically about LLM config governance and the LLM registry should offer an explicit Vault-aware assembly path.
- Store Vault references in `LlmConfigSnapshot` and resolve them at each request. Rejected because snapshots are defined as non-sensitive runtime values and should not carry secret governance state.
- Add secret resolution to `SET LLM`. Rejected because M28 limits `SET LLM` to non-sensitive runtime parameters and M33 splits mutation permissions, redaction, and audit into later planned changes.
- Implement redaction and audit together with Vault reference resolution. Rejected to keep the first M33 change focused and to preserve the roadmap's planned change boundaries.

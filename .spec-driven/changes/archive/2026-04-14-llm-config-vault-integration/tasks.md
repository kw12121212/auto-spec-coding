# Tasks: llm-config-vault-integration

## Implementation

- [x] Update the runtime LLM config delta spec to define Vault-backed provider authentication resolution and non-sensitive snapshot boundaries.
- [x] Update the Vault delta spec to define how LLM provider config uses the existing `vault:` reference behavior.
- [x] Add a Vault-aware LLM registry construction path that resolves provider config values before `LlmConfig.fromMap(...)`.
- [x] Ensure existing registry construction paths continue to accept already-resolved plaintext provider config.
- [x] Preserve runtime snapshot and persisted runtime config models without adding secret fields.

## Testing

- [x] Run validation build with `mvn -q -DskipTests compile`.
- [x] Run unit tests with `mvn -q -Dtest=DefaultLlmProviderRegistryTest,VaultResolverTest test`.

## Verification

- [x] Verify implementation matches the proposal scope and excludes permission, redaction, and audit behavior.
- [x] Run `node /home/wx766/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify llm-config-vault-integration` and resolve non-question validation errors.

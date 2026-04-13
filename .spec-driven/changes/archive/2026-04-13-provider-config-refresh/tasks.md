# Tasks: provider-config-refresh

## Implementation

- [x] Update the runtime LLM config delta spec to define how existing registry-managed clients pick up refreshed snapshots for later requests
- [x] Update the provider delta spec to define snapshot-aware provider client creation and cross-provider refresh behavior
- [x] Confirm implementation and test mappings for registry and provider files match the proposed spec scope

## Testing

- [x] Run validation build with `mvn -q -DskipTests compile`
- [x] Run unit tests with `mvn -q -Dtest=DefaultLlmProviderRegistryTest,OpenAiProviderTest,ClaudeProviderTest test`

## Verification

- [x] Run `node /home/code/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify provider-config-refresh` and confirm the proposal artifacts are valid

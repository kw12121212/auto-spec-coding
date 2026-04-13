# Tasks: llm-config-persistence

## Implementation

- [x] Add a runtime LLM config persistence contract and version model for the default non-sensitive snapshot only
- [x] Implement a Lealone-backed runtime LLM config store with append-only version history and restart recovery of the last valid default snapshot
- [x] Integrate the persisted default snapshot with runtime snapshot resolution without changing session-override semantics or in-flight request binding
- [x] Add unit tests covering persistence, restart recovery, version ordering, failed-write isolation, and internal restore behavior

## Testing

- [x] Validation: run `mvn -q -DskipTests compile` to confirm the planned implementation surface still matches the Maven build layout
- [x] Unit tests: run `mvn -q -Dtest=DefaultLlmProviderRegistryTest,LealoneRuntimeLlmConfigStoreTest test` for runtime LLM config persistence behavior during implementation

## Verification

- [x] Run `node "/home/code/.agents/skills/roadmap-recommend/scripts/spec-driven.js" verify llm-config-persistence` and resolve any artifact issues

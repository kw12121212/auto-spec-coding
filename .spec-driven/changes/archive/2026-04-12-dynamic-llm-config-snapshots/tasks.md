# Tasks: dynamic-llm-config-snapshots

## Implementation

- [x] Add a runtime `LlmConfigSnapshot` model and snapshot-aware provider/client resolution APIs in the LLM package
- [x] Extend `DefaultLlmProviderRegistry` to manage default and session-scoped snapshots with atomic replacement and fallback behavior
- [x] Add unit tests covering snapshot immutability, session isolation, default fallback, and in-flight request binding

## Testing

- [x] Validation: run `mvn -q -DskipTests compile` to confirm the planned implementation surface still matches the Maven build layout
- [x] Unit tests: run `mvn -q -Dtest=LlmConfigTest,DefaultLlmProviderRegistryTest,OpenAiProviderTest,ClaudeProviderTest test` for snapshot-related config and registry behavior during implementation

## Verification

- [x] Run `node "/home/code/.agents/skills/spec-driven-auto/scripts/spec-driven.js" verify dynamic-llm-config-snapshots` and resolve any artifact issues

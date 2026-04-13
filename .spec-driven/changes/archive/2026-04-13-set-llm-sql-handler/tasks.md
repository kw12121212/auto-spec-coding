# Tasks: set-llm-sql-handler

## Implementation

- [x] Extend the runtime LLM config spec with `SET LLM` statement behavior for supported non-sensitive parameters and all-or-nothing update semantics
- [x] Define how `SET LLM` updates are scoped to a session and how later requests observe the replacement snapshot without affecting in-flight requests
- [x] Map the proposal to the existing runtime LLM registry/store implementation and add unit tests for successful updates, invalid parameters, and session isolation

## Testing

- [x] Validation: run `mvn -q -DskipTests compile` to confirm the planned implementation surface still matches the Maven build layout
- [x] Unit tests: run `mvn -q -Dtest=DefaultLlmProviderRegistryTest,LealoneRuntimeLlmConfigStoreTest,SetLlmStatementParserTest test` for runtime LLM config SQL update behavior during implementation

## Verification

- [x] Run `node "/home/code/.agents/skills/spec-driven-auto/scripts/spec-driven.js" verify set-llm-sql-handler` and resolve any artifact issues

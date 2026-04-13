# Tasks: llm-config-events

## Implementation

 - [x] Extend the runtime LLM config spec with success-only `LLM_CONFIG_CHANGED` publication semantics for default-scope and session-scope runtime updates
 - [x] Extend the event system spec to add `LLM_CONFIG_CHANGED` to the public event type surface
 - [x] Map the proposal to the existing runtime registry and event tests, covering default replace, session replace, `SET LLM`, clear-session fallback, and failed updates not emitting success events

## Testing

 - [x] Validation: run `mvn -q -DskipTests compile` to confirm the planned implementation surface still matches the Maven build layout
 - [x] Unit tests: run `mvn -q -Dtest=DefaultLlmProviderRegistryTest,EventSystemTest test` for runtime config event publication behavior

## Verification

 - [x] Run `node "/home/code/.agents/skills/roadmap-recommend/scripts/spec-driven.js" verify llm-config-events` and resolve any artifact issues

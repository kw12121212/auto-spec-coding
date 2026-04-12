# Tasks: tool-result-filter

## Implementation

- [x] Add a ToolResult filtering contract and default implementation under `src/main/java/org/specdriven/agent/agent/`.
- [x] Model explicit filter inputs for current-turn text, requested tool names, candidate messages, and optional retention metadata without depending on orchestrator internals.
- [x] Retain non-tool messages in original order while evaluating prior `ToolMessage` instances for retention and relevance.
- [x] Ensure `ContextRetentionLevel.MANDATORY` tool messages are preserved even when relevance is low.
- [x] Add an opt-in helper for producing an `LlmRequest` with the filtered message list while preserving system prompt, tools, temperature, max tokens, and extra parameters.
- [x] Keep existing LLM provider, orchestrator, loop, and request-building behavior unchanged unless the new filter is explicitly invoked.

## Testing

- [x] Add `ToolResultFilterTest` under `src/test/java/org/specdriven/agent/agent/` covering relevant result retention, irrelevant result removal, mandatory retention precedence, stable ordering, and request helper parameter preservation.
- [x] Run lint or validation command `mvn -DskipTests compile`.
- [x] Run focused unit test command `mvn -Dtest=ToolResultFilterTest test`.
- [x] Run full unit test command `mvn test`.

## Verification

- [x] Run proposal validation command `node /home/code/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify tool-result-filter`.
- [x] Confirm the implementation matches the M27 `tool-result-filter` roadmap item without adding summarization or full smart-context injector integration.
- [x] Confirm the delta spec describes observable behavior and that tests verify behavior rather than private implementation details.

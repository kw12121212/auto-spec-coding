# Tasks: conversation-summarizer

## Implementation

- [x] Add a Conversation summarization contract and default implementation under `src/main/java/org/specdriven/agent/agent/`.
- [x] Model explicit summarization inputs for candidate messages, recent-message retention, token budgets, summary budgets, and optional retention metadata without depending on orchestrator internals.
- [x] Preserve system messages, recent-window messages, and `ContextRetentionLevel.MANDATORY` messages while identifying older eligible history for compression.
- [x] Produce a bounded deterministic summary message for compressed older history, including observable counts and role/tool context.
- [x] Add an opt-in helper for producing an `LlmRequest` with the summarized message list while preserving system prompt, tools, temperature, max tokens, and extra parameters.
- [x] Keep existing LLM provider, orchestrator, loop, context-window tracking, and request-building behavior unchanged unless the new summarizer is explicitly invoked.

## Testing

- [x] Add `ConversationSummarizerTest` under `src/test/java/org/specdriven/agent/agent/` covering summary generation, no-op behavior under threshold, recent-window preservation, system-message preservation, mandatory retention precedence, stable ordering, and request helper parameter preservation.
- [x] Run lint or validation command `mvn -DskipTests compile`.
- [x] Run focused unit test command `mvn -Dtest=ConversationSummarizerTest test`.
- [x] Run full unit test command `mvn test`.

## Verification

- [x] Run proposal validation command `node /home/code/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify conversation-summarizer`.
- [x] Confirm the implementation matches the M27 `conversation-summarizer` roadmap item without adding full smart-context injector integration.
- [x] Confirm the delta spec describes observable behavior and that tests verify behavior rather than private implementation details.

# conversation-summarizer

## What

Add the M27 `conversation-summarizer` change. This change defines and implements a focused `ConversationSummarizer` capability for LLM request preparation: when conversation history exceeds configured limits, older non-mandatory history is compressed into a bounded summary while recent messages and mandatory recovery, question, answer replay, audit, and active tool-call context remain available.

## Why

M27 already has relevance scoring, mandatory context retention, and ToolResult filtering. The next dependency-ordered step is to add the summarization behavior that M27 requires before the broader `smart-context-injector` integration can wrap `LlmClient`, `DefaultOrchestrator`, or `LoopDriver`.

The current context-window support tracks token usage but does not provide a behavior for replacing simple truncation with a summary. Long autonomous loops need a deterministic, testable summarization contract so older history can be compressed without silently dropping context that is required for recovery or question handling.

## Scope

In scope:

- Define observable Conversation summarization behavior for candidate message lists used to build `LlmRequest` objects.
- Add a public summarization contract and default implementation that accepts explicit limits, recent-message retention settings, candidate messages, and optional retention metadata.
- Preserve system messages and recent messages according to the configured sliding window.
- Preserve messages classified as mandatory by `ContextRetentionPolicy` even when they are outside the recent-message window.
- Replace older eligible history with a bounded summary message that reports compressed message counts and role/tool context.
- Add an opt-in helper for producing an `LlmRequest` with summarized messages while preserving non-message request parameters.
- Add focused JUnit 5 tests covering summary generation, recent-window preservation, mandatory retention precedence, system-message preservation, token-budget behavior, stable ordering, null/empty handling, and request helper parameter preservation.

Out of scope:

- Full `SmartContextInjector` integration with `LlmClient`, `DefaultOrchestrator`, or `LoopDriver`.
- Provider-specific request serialization changes.
- LLM-generated abstractive summaries or embedding-based semantic compression.
- ToolResult relevance filtering changes beyond composing cleanly with the existing filter in later work.
- Fixed evaluation-set benchmark wiring and token-reduction acceptance thresholds; those remain part of the later integration change.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- Existing LLM providers must continue serializing `LlmRequest` objects using their current provider-specific behavior.
- Existing callers that do not explicitly invoke conversation summarization must see unchanged request contents.
- `ContextRelevanceScorer`, `ContextRetentionPolicy`, and `ToolResultFilter` observable behavior must remain backward compatible.
- `ContextWindowManager` token usage tracking must remain unchanged unless a caller explicitly uses the new summarization behavior.

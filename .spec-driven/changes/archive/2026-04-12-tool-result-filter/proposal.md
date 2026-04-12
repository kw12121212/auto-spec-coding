# tool-result-filter

## What

Add the M27 `tool-result-filter` change. This change defines and implements a focused ToolResult filtering layer for LLM request preparation: prior tool result messages are evaluated against the current turn, mandatory retention metadata takes precedence, and only retained or relevant tool results are forwarded into the optimized `LlmRequest`.

## Why

M27 already has `ContextRelevanceScorer` and `ContextRetentionPolicy`. The next dependency-ordered step is to consume those contracts before broader summarization or full smart-context integration work begins. Filtering unrelated tool output first reduces token pressure for long-running autonomous loops while preserving recovery, question escalation, answer replay, audit traceability, and active tool-call context.

## Scope

In scope:

- Define observable ToolResult filtering behavior for LLM request message lists.
- Add a public filtering contract and default implementation that accepts explicit current-turn input and prior messages.
- Preserve mandatory context whenever the retention policy classifies it as `MANDATORY`.
- Use the relevance scorer to keep relevant prior tool results and remove irrelevant, non-mandatory prior tool results.
- Preserve non-tool conversation messages in their original order.
- Add focused JUnit 5 tests covering filtering, retention precedence, stable ordering, null/empty handling, and request-building integration.

Out of scope:

- Conversation summarization or semantic compression.
- Full `SmartContextInjector` integration with `DefaultOrchestrator` or `LoopDriver`.
- Provider-specific request serialization changes.
- Embedding-based semantic relevance.
- Token reduction benchmarks or fixed evaluation set wiring.

## Unchanged Behavior

- Existing LLM providers must continue serializing `LlmRequest` objects using their current provider-specific behavior.
- Existing callers that do not explicitly use the ToolResult filter must see unchanged request contents.
- `ContextRelevanceScorer` and `ContextRetentionPolicy` observable behavior must remain backward compatible.

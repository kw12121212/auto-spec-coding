# context-relevance-scorer

## What

Define the first spec slice of M27 by introducing a `ContextRelevanceScorer` contract and a default scorer that ranks tool-result relevance for the current turn using observable keyword and tool-name matching rules.

## Why

M27 depends on a stable relevance contract before the later `tool-result-filter` and `smart-context-injector` changes can specify filtering and integration behavior precisely. Starting with the scorer isolates the hardest semantic boundary first and lets the rest of the milestone build on a concrete, testable foundation.

## Scope

- In scope: the scorer interface, its scoring inputs and outputs, the default keyword-based scoring behavior, deterministic tie handling, and unit-testable edge cases
- In scope: observable behavior for how the scorer treats tool names, current-turn tool calls, and message text when computing relevance
- Out of scope: filtering `ToolMessage` instances out of outbound LLM requests
- Out of scope: conversation summarization, token-budget enforcement, caching, or orchestrator integration changes
- Out of scope: embedding-based or provider-specific semantic ranking

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- Existing `DefaultOrchestrator`, `LlmClient`, and provider request/response behavior remains unchanged
- `ContextWindowManager` token accounting and cropping behavior remains unchanged
- No HTTP, JSON-RPC, SDK, or loop state-machine behavior changes in this proposal

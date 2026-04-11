# Design: context-relevance-scorer

## Approach

Add a small scorer contract in the existing `org.specdriven.agent.agent` package so later M27 changes can depend on a stable relevance API without forcing filtering or summarization behavior into the first proposal. The initial delta spec defines a default keyword-based scorer that compares normalized current-turn text and requested tool-call names against prior tool-result text and tool name, returning a deterministic numeric score suitable for descending sort order.

The proposal keeps the scorer inputs explicit and observable. Instead of binding the contract to `DefaultOrchestrator` internals, the scorer operates on current-turn text, requested tool-call names, and prior tool-result data. That keeps the contract reusable by both future request-building logic and tests.

## Key Decisions

- Define the scorer as a standalone contract before any filtering decorator so later changes can build on a settled relevance model
- Keep the first implementation keyword-based rather than semantic/embedding-based to match milestone notes and avoid premature provider coupling
- Specify deterministic numeric scoring so later changes can sort results without hidden ranking state
- Keep the proposal limited to scorer behavior and test coverage; do not spec request filtering, summarization, or caching in this change

## Alternatives Considered

- Start with `tool-result-filter` first
  - Rejected because filtering behavior depends on a relevance contract; otherwise the later proposal would have to redefine ranking semantics implicitly.
- Start with `conversation-summarizer` first
  - Rejected because summarization is a larger behavioral surface and does not unblock tool-result selection as directly.
- Use embeddings or provider-specific semantic ranking in the first scorer
  - Rejected because the roadmap explicitly frames the first implementation around simple matching and because semantic ranking would expand scope into provider coupling, latency, and additional configuration.

# Design: smart-context-injector

## Approach

Introduce `SmartContextInjector` in the existing `org.specdriven.agent.agent` context package as an `LlmClient` decorator. The decorator should accept a delegate client plus a small configuration object for recent-message retention, token budget, summary budget, and optional current-turn metadata. For each outgoing `chat(...)` call, it should produce an optimized message list by applying the existing `ToolResultFilter` first and the existing `ConversationSummarizer` second, then call the delegate exactly once with the optimized request.

For structured `LlmRequest` calls, the decorator should preserve system prompt, tools, temperature, max tokens, and extra parameters. For legacy `chat(List<Message>)` calls, it should optimize the list and delegate through the existing list-based API. This keeps provider behavior unchanged while making optimization usable by current orchestrator code, which calls `LlmClient.chat(List<Message>)`.

Current-turn data should be explicit when callers can provide it. When no explicit metadata is available, the default behavior should derive the current-turn text from the latest user-visible message and use an empty requested-tool-name list. This fallback keeps optimization deterministic and avoids parsing assistant text that may only be a formatted representation of tool-call responses.

Integrate with the autonomous loop through `SpecDrivenPipeline`: when `LoopConfig.contextBudget()` is configured, the phase client should be wrapped with the smart context injector before it is passed into `DefaultOrchestrator`. `DefaultOrchestrator` itself should not need a new public method; integration happens through the `LlmClient` abstraction it already consumes. Loop state transitions, question routing, answer handling, and context-exhaustion events remain unchanged.

Add a small fixed evaluation fixture under test resources or test code. It should compare baseline messages with optimized messages, assert an estimated token reduction of at least 30%, and verify that required recovery/question/answer/audit markers remain present.

## Key Decisions

- Compose existing M27 primitives instead of duplicating filtering or summarization logic inside the injector.
- Run filtering before summarization so irrelevant tool output is removed before summary budgeting is applied.
- Keep `DefaultOrchestrator.run(...)` unchanged; use the `LlmClient` decorator boundary for integration.
- Use `LoopConfig.contextBudget()` as the loop integration trigger so existing loops without context budgeting remain unchanged.
- Treat token counts as deterministic estimates for evaluation and request preparation; actual provider usage remains reported by `LlmResponse.usage()`.
- Keep the default current-turn extractor conservative: latest user-visible text is reliable, while assistant tool-call intent is not yet stored as structured conversation metadata.
- Verify benchmark behavior with a fixed local fixture instead of live LLM calls, keeping tests independent and repeatable.

## Alternatives Considered

- Modify provider clients to run context optimization internally: ruled out because filtering and summarization are provider-agnostic and should happen before serialization.
- Add new `DefaultOrchestrator` overloads for smart-context settings: ruled out because the existing orchestrator already accepts an `LlmClient`, and a decorator keeps the public surface smaller.
- Enable smart context globally for every `LlmClient`: ruled out because existing plain callers must remain unchanged unless they opt into the wrapper or loop context budget path.
- Use LLM-generated summaries: ruled out for this integration because the completed summarizer is deterministic and local, while LLM-generated summaries would add cost, latency, and failure paths.
- Parse `AssistantMessage.toString()` output to recover requested tool names: ruled out because it is not a stable observable contract; explicit metadata can be supported later without relying on formatted debug text.

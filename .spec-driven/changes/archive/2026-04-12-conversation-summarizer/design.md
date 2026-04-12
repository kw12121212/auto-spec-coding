# Design: conversation-summarizer

## Approach

Introduce a small summarization API in the existing `org.specdriven.agent.agent` context package. The API should accept an explicit input object containing candidate messages, recent-window size, token budget, summary token budget, and optional retention metadata keyed by observable message or tool-call identifiers. It should return an immutable summarized message list suitable for constructing a subsequent `LlmRequest`.

The default summarizer should classify messages in their original order. System messages are always preserved. The most recent configured number of messages are preserved. Messages classified by `ContextRetentionPolicy` as `MANDATORY` are preserved even when older than the recent window. Older eligible messages are compressed into one summary message placed before the preserved recent history, after any preserved system messages.

The first implementation should be deterministic and local: it should build a bounded extractive summary from observable message fields such as role, content snippets, tool name, tool-call ID, message counts, and token estimates. It should not call an LLM or depend on provider-specific behavior. A later `smart-context-injector` change can decide where this summarizer is composed with the existing ToolResult filter and LLM request flow.

If a helper for applying summarization to an existing `LlmRequest` is added, it should replace only the request message list and preserve system prompt, tools, temperature, max tokens, and extra parameters.

## Key Decisions

- Keep summarization explicit and opt-in so existing callers remain unchanged until the planned integration change consumes it.
- Use a deterministic local summary for the first implementation because it can be tested without external providers and avoids spending tokens to reduce tokens.
- Preserve mandatory context before enforcing compression so recovery execution, question escalation, answer replay, audit traceability, and active tool-call correlation are not lost.
- Preserve system messages separately from the recent-message window because provider request semantics and instruction context must stay stable.
- Return immutable outputs to keep request-preparation behavior deterministic for later decorator composition.
- Treat token limits as observable budgets for summary output, not as a promise of provider-specific exact token counts.

## Alternatives Considered

- Integrate directly into `DefaultOrchestrator`: ruled out for this change because M27 reserves orchestration and loop integration for `smart-context-injector`.
- Generate summaries by making another LLM call: ruled out for the first version because it adds latency, cost, provider dependency, and failure modes before the local behavior is proven.
- Extend `ContextWindowManager` to crop and summarize directly: ruled out because it currently tracks usage, and folding request mutation into it would mix two responsibilities.
- Drop older messages without a summary: ruled out because the roadmap item explicitly calls for replacing simple truncation with summarization.
- Summarize every old message including mandatory context: ruled out because M27 retention policy requires mandatory context to survive optimization.

# Design: llm-streaming

## Approach

### SSE Parsing

Create a shared `SseParser` utility that reads lines from an `InputStream` and emits parsed events. Each provider wraps `SseParser` with provider-specific event handlers that map SSE data lines to `LlmStreamCallback` calls:

1. Both providers add `stream: true` to the request body
2. Both switch from `BodyHandlers.ofString()` to `BodyHandlers.ofInputStream()`
3. `SseParser` reads `data: ...` lines, skips comments (`: ...`) and empty lines
4. Provider-specific handlers extract delta content and call `callback.onToken()`
5. On stream end, providers accumulate the full response and call `callback.onComplete()`

### Provider-Specific Event Handling

**OpenAI streaming format:**
- Each chunk: `data: {"choices":[{"delta":{"content":"..."}}]}`
- Terminator: `data: [DONE]`
- Tool call deltas: `choices[0].delta.tool_calls` (accumulate across chunks)

**Claude streaming format:**
- Events: `message_start`, `content_block_start`, `content_block_delta`, `content_block_stop`, `message_delta`, `message_stop`
- Delta content: `content_block_delta.delta.text` for text, `content_block_delta.delta.partial_json` for tool input
- Usage: `message_delta.usage.output_tokens`

### Token Counting

Create `TokenCounter` backed by the jtokkit library for accurate token counting. jtokkit (~50KB, no transitive deps) supports GPT-4/Claude tokenizer encodings. The counter is used by `ContextWindowManager` which:
- Tracks cumulative token usage per conversation
- Estimates incoming request size before sending
- Reports when usage approaches the model's context window limit

### Streaming Retry

Retry logic for streaming follows the same exponential backoff policy as synchronous requests, but retries only apply to connection-level failures before the stream starts. Once the first token is received, retries are not attempted — `callback.onError()` is called instead.

## Key Decisions

1. **Shared SseParser** — Both providers use the same line-level SSE parser; only event interpretation differs. Avoids code duplication.
2. **Character-based token estimation for v1** — Avoids adding a tokenizer dependency. Accurate enough for context window boundary detection. Can be replaced with tiktoken-equivalent later.
3. **No retry mid-stream** — Once streaming begins, connection errors go to `onError()`. Retrying mid-stream would require complex state management and duplicate token delivery.
4. **chatStreaming as override, not new interface** — The `LlmClient.chatStreaming()` method already exists; providers simply override it.

## Alternatives Considered

1. **Reactive streams (Flow.Publisher)** — Java 9+ Flow API would provide backpressure but adds complexity unnecessary for the agent's consumption pattern (callback is always ready). Rejected for v1.
2. **External tokenizer (jtokkit)** — Selected. Accurate counting, lightweight (~50KB), no transitive dependencies. Worth the small dependency for reliable context window boundary detection.
3. **Separate streaming client interface** — Instead of adding `chatStreaming()` to `LlmClient`, create a new `StreamingLlmClient`. Rejected because the method already exists with a default implementation.

# Tasks: llm-streaming

## Implementation

- [x] Create `SseParser` utility class in `org.specdriven.agent.agent` that reads SSE events from an `InputStream`
- [x] Implement `OpenAiClient.chatStreaming()` — add `stream: true` to request, parse OpenAI SSE chunks, call `onToken`/`onComplete`/`onError`
- [x] Implement `ClaudeClient.chatStreaming()` — add `stream: true` to request, parse Claude SSE events (`message_start`, `content_block_delta`, `message_delta`, `message_stop`), call `onToken`/`onComplete`/`onError`
- [x] Add streaming retry logic — apply exponential backoff for connection failures before stream start, no retry after first token
- [x] Add jtokkit dependency to `pom.xml`
- [x] Create `TokenCounter` utility in `org.specdriven.agent.agent` backed by jtokkit for accurate token counting
- [x] Create `ContextWindowManager` in `org.specdriven.agent.agent` to track cumulative token usage against model context window limit

## Testing

- [x] `mvn compile` — build validation: verify project compiles with all new classes
- [x] `mvn test -pl . -Dtest=SseParserTest` — unit tests for SSE line parsing (data lines, comments, empty lines, multi-line data)
- [x] `mvn test -pl . -Dtest=OpenAiStreamingTest` — unit tests for OpenAI streaming (mock SSE stream, text deltas, tool call deltas, `[DONE]` terminator, usage parsing)
- [x] `mvn test -pl . -Dtest=ClaudeStreamingTest` — unit tests for Claude streaming (mock SSE stream, content_block_delta, tool_use accumulation, message_stop, usage from message_delta)
- [x] `mvn test -pl . -Dtest=TokenCounterTest` — unit tests for token estimation (text, messages, empty input)
- [x] `mvn test -pl . -Dtest=ContextWindowManagerTest` — unit tests for capacity tracking (add usage, remaining, canFit, reset)
- [x] `mvn test` — full test suite passes (no regressions)

## Verification

- [x] Verify `OpenAiClient.chatStreaming()` overrides the default `UnsupportedOperationException`
- [x] Verify `ClaudeClient.chatStreaming()` overrides the default `UnsupportedOperationException`
- [x] Verify existing synchronous `chat()` tests still pass unchanged
- [x] Verify streaming tests use mock SSE streams (no external HTTP calls)
- [x] Verify delta spec `llm-provider.md` matches what was actually built

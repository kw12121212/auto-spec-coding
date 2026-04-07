# llm-streaming

## What

Implement streaming LLM response handling for both OpenAI and Claude providers: SSE parsing, token-by-token callback delivery, and token counting with context window management. This covers the two planned changes from M19: `llm-streaming` and `llm-token-counter`.

## Why

M5 (LLM Provider Layer) established synchronous `chat()` for both providers. Production agents require streaming for real-time token feedback during long-running responses. Token counting is needed to prevent context window overflow during multi-turn tool-call loops. The `LlmStreamCallback` interface and `LlmClient.chatStreaming()` default method already exist but no provider implements them.

## Scope

- SSE response parsing for OpenAI Chat Completions streaming format
- SSE response parsing for Claude Messages API streaming format
- `chatStreaming()` implementation in `OpenAiClient` and `ClaudeClient`
- `TokenCounter` utility for token estimation and context window boundary detection
- `ContextWindowManager` for tracking cumulative token usage across a conversation

## Unchanged Behavior

- Existing synchronous `chat()` methods on both providers MUST continue to work identically
- `LlmStreamCallback` interface signature (onToken, onComplete, onError) MUST NOT change
- `LlmClient.chatStreaming()` default behavior (throws `UnsupportedOperationException`) remains for providers that do not override it
- `LlmRequest`, `LlmResponse`, `LlmUsage` data structures MUST NOT change
- Retry policies for streaming requests follow the same rules as synchronous requests

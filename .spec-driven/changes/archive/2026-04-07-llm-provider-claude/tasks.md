# Tasks: llm-provider-claude

## Implementation

- [x] Confirm `JsonWriter` and `JsonReader` from `llm-provider-openai` are accessible (package-private vs package, adjust visibility if needed)
- [x] Implement `ClaudeProvider` — implements `LlmProvider`, holds `LlmConfig`, creates `ClaudeClient`, no-op `close()`
- [x] Implement `ClaudeClient` — implements `LlmClient`:
  - [x] `chat(LlmRequest)` — serialize request to Claude Messages API JSON, POST via `java.net.http.HttpClient`, parse response
  - [x] `chat(List<Message>)` — delegate to `chat(LlmRequest.of(messages))`
  - [x] Request serialization: system prompt as top-level `"system"` field (not in messages array); map `UserMessage`/`AssistantMessage` to `{role, content}` pairs; map `ToolMessage` to user role with `tool_result` content block; map `ToolSchema` to `{name, description, input_schema}` format (no `type: function` wrapper); include `max_tokens` (default 4096 if not set); include `temperature` if set
  - [x] Required headers: `x-api-key`, `anthropic-version: 2023-06-01`, `Content-Type: application/json`
  - [x] Response parsing: iterate `content` array, `type: text` → `TextResponse`, `type: tool_use` → `ToolCallResponse`; map `usage.input_tokens`/`usage.output_tokens` to `LlmUsage`; map `stop_reason` to finish reason
  - [x] Retry logic: exponential backoff on 429/5xx, respect `Retry-After` header, up to `maxRetries` attempts
  - [x] Error handling: throw descriptive exceptions on non-retryable errors (400/401/403/404) and exhausted retries
- [x] Implement `ClaudeProviderFactory` — implements `LlmProviderFactory`, wraps `ClaudeProvider::new`, registers under key `"claude"`

## Testing

- [x] lint: run `mvn compile` to verify compilation succeeds with no errors
- [x] unit tests: run `mvn test` to verify all existing + new unit tests pass
- [x] `ClaudeProviderTest` — test provider creation, client creation, close, factory
- [x] `ClaudeClientTest` — test with `com.sun.net.httpserver.HttpServer` as mock:
  - [x] Text response parsing (`content[0].type == "text"`)
  - [x] Tool call response parsing (`content[*].type == "tool_use"`)
  - [x] Usage parsing (`input_tokens`/`output_tokens` → `LlmUsage`)
  - [x] System prompt serialized as top-level `system` field, NOT as a message
  - [x] Tool schema serialization uses `input_schema` (not `parameters`), no `type: function` wrapper
  - [x] `ToolMessage` serialized as `user` role with `tool_result` content block
  - [x] `max_tokens` defaults to 4096 when not set in request
  - [x] Required headers present: `x-api-key`, `anthropic-version`, `Content-Type`
  - [x] Retry on 429 with `Retry-After` header
  - [x] Retry on 500 with exponential backoff
  - [x] No retry on 401
  - [x] Exhausted retries throws exception

## Verification

- [x] Verify `ClaudeProvider` implements `LlmProvider` interface correctly
- [x] Verify `ClaudeClient` implements `LlmClient` interface correctly
- [x] Verify `ClaudeProviderFactory` works with `DefaultLlmProviderRegistry.fromConfig()` using key `"claude"`
- [x] Verify all existing tests still pass (`mvn test`)
- [x] Verify delta spec reflects what was actually built

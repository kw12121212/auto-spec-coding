# Tasks: llm-provider-openai

## Implementation

- [x] Add `callId` field to `ToolCall` record and `toolCallId` field to `ToolMessage` record; update all existing call sites and `DefaultOrchestrator`
- [x] Create `JsonWriter` utility class in `org.specdriven.agent.json` for manual JSON serialization (object, array, string escaping, number, boolean)
- [x] Create `JsonReader` utility class in `org.specdriven.agent.json` for JSON response parsing (extract nested fields, parse arrays, parse numbers)
- [x] Implement `OpenAiProvider` — implements `LlmProvider`, holds `LlmConfig`, creates `OpenAiClient`, no-op `close()`
- [x] Implement `OpenAiClient` — implements `LlmClient`:
  - [x] `chat(LlmRequest)` — serialize request to OpenAI JSON, POST via `java.net.http.HttpClient`, parse response
  - [x] `chat(List<Message>)` — delegate to `chat(LlmRequest.of(messages))`
  - [x] Request serialization: map `Message` subtypes to OpenAI format, inject system prompt, include `ToolSchema` as tools
  - [x] Response parsing: parse `choices[0].message` into `TextResponse` or `ToolCallResponse`, parse `usage` into `LlmUsage`
  - [x] Retry logic: exponential backoff on 429/5xx, respect `Retry-After` header, up to `maxRetries` attempts
  - [x] Error handling: throw descriptive exceptions on non-retryable errors (400/401/403/404) and exhausted retries
- [x] Implement `OpenAiProviderFactory` — implements `LlmProviderFactory`, wraps `OpenAiProvider::new`

## Testing

- [x] lint: run `mvn compile` to verify compilation succeeds with no errors
- [x] unit tests: run `mvn test` to verify all existing + new unit tests pass
- [x] `OpenAiProviderTest` — test provider creation, client creation, close, factory
- [x] `OpenAiClientTest` — test with `com.sun.net.httpserver.HttpServer` as mock:
  - [x] Text response parsing
  - [x] Tool call response parsing
  - [x] Usage parsing
  - [x] System prompt injection in serialized request
  - [x] Tool schema serialization
  - [x] Message role mapping (all 4 message types)
  - [x] Retry on 429 with Retry-After header
  - [x] Retry on 500 with exponential backoff
  - [x] No retry on 401
  - [x] Exhausted retries throws exception
  - [x] Custom base URL routes to correct endpoint
- [x] `JsonWriterTest` — test JSON serialization correctness
- [x] `JsonReaderTest` — test JSON parsing correctness

## Verification

- [x] Verify `OpenAiProvider` implements `LlmProvider` interface correctly
- [x] Verify `OpenAiClient` implements `LlmClient` interface correctly
- [x] Verify `OpenAiProviderFactory` works with `DefaultLlmProviderRegistry.fromConfig()` using key `"openai"`
- [x] Verify all existing tests still pass (`mvn test`)
- [x] Verify delta spec reflects what was actually built

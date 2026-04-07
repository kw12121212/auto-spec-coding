# Tasks: llm-provider-openai

## Implementation

- [ ] Create `JsonWriter` utility class for manual JSON serialization (object, array, string escaping, number, boolean)
- [ ] Create `JsonReader` utility class for JSON response parsing (extract nested fields, parse arrays, parse numbers)
- [ ] Implement `OpenAiProvider` — implements `LlmProvider`, holds `LlmConfig`, creates `OpenAiClient`, no-op `close()`
- [ ] Implement `OpenAiClient` — implements `LlmClient`:
  - [ ] `chat(LlmRequest)` — serialize request to OpenAI JSON, POST via `java.net.http.HttpClient`, parse response
  - [ ] `chat(List<Message>)` — delegate to `chat(LlmRequest.of(messages))`
  - [ ] Request serialization: map `Message` subtypes to OpenAI format, inject system prompt, include `ToolSchema` as tools
  - [ ] Response parsing: parse `choices[0].message` into `TextResponse` or `ToolCallResponse`, parse `usage` into `LlmUsage`
  - [ ] Retry logic: exponential backoff on 429/5xx, respect `Retry-After` header, up to `maxRetries` attempts
  - [ ] Error handling: throw descriptive exceptions on non-retryable errors (400/401/403/404) and exhausted retries
- [ ] Implement `OpenAiProviderFactory` — implements `LlmProviderFactory`, wraps `OpenAiProvider::new`

## Testing

- [ ] lint: run `mvn compile` to verify compilation succeeds with no errors
- [ ] unit tests: run `mvn test` to verify all existing + new unit tests pass
- [ ] `OpenAiProviderTest` — test provider creation, client creation, close, factory
- [ ] `OpenAiClientTest` — test with `com.sun.net.httpserver.HttpServer` as mock:
  - [ ] Text response parsing
  - [ ] Tool call response parsing
  - [ ] Usage parsing
  - [ ] System prompt injection in serialized request
  - [ ] Tool schema serialization
  - [ ] Message role mapping (all 4 message types)
  - [ ] Retry on 429 with Retry-After header
  - [ ] Retry on 500 with exponential backoff
  - [ ] No retry on 401
  - [ ] Exhausted retries throws exception
  - [ ] Custom base URL routes to correct endpoint
- [ ] `JsonWriterTest` — test JSON serialization correctness
- [ ] `JsonReaderTest` — test JSON parsing correctness

## Verification

- [ ] Verify `OpenAiProvider` implements `LlmProvider` interface correctly
- [ ] Verify `OpenAiClient` implements `LlmClient` interface correctly
- [ ] Verify `OpenAiProviderFactory` works with `DefaultLlmProviderRegistry.fromConfig()` using key `"openai"`
- [ ] Verify all existing tests still pass (`mvn test`)
- [ ] Verify delta spec reflects what was actually built

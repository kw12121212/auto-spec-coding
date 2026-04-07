# Tasks: llm-provider-interface

## Implementation

- [x] Create `LlmConfig` record with fields (baseUrl, apiKey, model, timeout, maxRetries) and `fromMap()` factory method
- [x] Create `LlmUsage` record with fields (promptTokens, completionTokens, totalTokens)
- [x] Create `ToolSchema` record with fields (name, description, parameters) and `from(Tool)` factory method
- [x] Create `LlmRequest` record with fields (messages, systemPrompt, tools, temperature, maxTokens) and `of(List<Message>)` factory
- [x] Enhance `LlmResponse` sealed interface: add usage and finishReason to TextResponse and ToolCallResponse
- [x] Create `LlmStreamCallback` interface with onToken, onComplete, onError methods
- [x] Create `LlmProvider` interface with config(), createClient(), close() methods
- [x] Add `chat(LlmRequest)` overload to `LlmClient` with default implementation delegating to `chat(List<Message>)`

## Testing

- [x] Run `mvn compile` to validate compilation with no errors
- [x] Write unit tests for `LlmConfig.fromMap()` — valid map, missing keys, null map
- [x] Write unit tests for `ToolSchema.from(Tool)` — with parameters, empty parameters, null safety
- [x] Write unit tests for `LlmRequest` construction and defaults
- [x] Write unit tests for enhanced `LlmResponse` — usage and finishReason fields
- [x] Write unit test verifying `LlmClient.chat(LlmRequest)` default delegation works
- [x] Run `mvn test` to verify all existing and new unit tests pass

## Verification

- [x] Verify DefaultOrchestrator compiles and tests pass unchanged (backward compatibility)
- [x] Verify all new types are in `org.specdriven.agent.agent` package
- [x] Verify no external JSON dependencies introduced

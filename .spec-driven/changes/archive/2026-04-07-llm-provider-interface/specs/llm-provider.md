# llm-provider.md

## ADDED Requirements

### Requirement: LlmProvider Interface
The system MUST define a `LlmProvider` interface that represents a configured LLM provider capable of creating `LlmClient` instances.

#### Scenario: Create client from provider
- GIVEN a `LlmProvider` instance configured with valid `LlmConfig`
- WHEN `createClient()` is called
- THEN it MUST return a `LlmClient` instance ready for use

#### Scenario: Provider lifecycle
- GIVEN a `LlmProvider` instance
- WHEN `close()` is called
- THEN the provider MUST release all associated resources

### Requirement: LlmConfig Record
The system MUST define an immutable `LlmConfig` record holding provider configuration.

#### Scenario: Build from map
- GIVEN a `Map<String, String>` with keys `baseUrl`, `apiKey`, `model`, `timeout`
- WHEN `LlmConfig.fromMap(map)` is called
- THEN it MUST return a `LlmConfig` with the mapped values and sensible defaults for missing keys

#### Scenario: Required fields
- GIVEN a `LlmConfig`
- THEN it MUST expose `baseUrl()` and `apiKey()` as non-null strings

### Requirement: LlmRequest Record
The system MUST define an immutable `LlmRequest` record encapsulating all parameters for an LLM call.

#### Scenario: Minimal request
- GIVEN a list of messages
- WHEN `LlmRequest.of(messages)` is called
- THEN it MUST return a request with default temperature, no tools, and no system prompt

#### Scenario: Full request with tools
- GIVEN messages, system prompt, tool schemas, temperature, and maxTokens
- WHEN a `LlmRequest` is constructed with all parameters
- THEN all fields MUST be accessible via accessor methods

### Requirement: Enhanced LlmClient Interface
The system MUST enhance the existing `LlmClient` interface with a new overload that accepts `LlmRequest`.

#### Scenario: Chat with request object
- GIVEN an `LlmClient` instance and an `LlmRequest` containing messages and tool definitions
- WHEN `chat(LlmRequest)` is called
- THEN it MUST return an `LlmResponse`

#### Scenario: Backward compatibility
- GIVEN the existing `chat(List<Message>)` method signature
- WHEN existing code calls it
- THEN it MUST continue to work without modification

### Requirement: Enhanced LlmResponse
The system MUST extend the existing `LlmResponse` sealed interface with usage and finish reason metadata.

#### Scenario: Text response with usage
- GIVEN an LLM returns a text completion
- WHEN the `TextResponse` is created
- THEN it MUST include `content()`, `usage()`, and `finishReason()`

#### Scenario: Tool call response with usage
- GIVEN an LLM returns tool call requests
- WHEN the `ToolCallResponse` is created
- THEN it MUST include `toolCalls()`, `usage()`, and `finishReason()`

### Requirement: LlmUsage Record
The system MUST define an immutable `LlmUsage` record for token usage statistics.

#### Scenario: Usage fields
- GIVEN an `LlmUsage` instance
- THEN it MUST expose `promptTokens()`, `completionTokens()`, and `totalTokens()` as non-negative integers

### Requirement: ToolSchema Record
The system MUST define a `ToolSchema` record that represents a tool definition in LLM-compatible format.

#### Scenario: Convert from Tool
- GIVEN a `Tool` instance with name, description, and parameters
- WHEN `ToolSchema.from(tool)` is called
- THEN it MUST return a `ToolSchema` with the tool's name, description, and parameters as a JSON Schema map

#### Scenario: No parameters tool
- GIVEN a `Tool` with an empty parameter list
- WHEN `ToolSchema.from(tool)` is called
- THEN it MUST return a `ToolSchema` with an empty parameters map

### Requirement: LlmStreamCallback Interface
The system MUST define a `LlmStreamCallback` interface for streaming LLM response handling.

#### Scenario: Token callback
- GIVEN a streaming LLM response producing tokens
- WHEN each token is received
- THEN `onToken(String)` MUST be called with the token text

#### Scenario: Stream completion
- GIVEN a streaming LLM response that has finished
- WHEN all tokens have been delivered
- THEN `onComplete(LlmResponse)` MUST be called with the complete response

#### Scenario: Stream error
- GIVEN a streaming LLM response that encounters an error
- WHEN the error occurs
- THEN `onError(Exception)` MUST be called with the error

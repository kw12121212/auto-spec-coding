---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/ClaudeClient.java
    - src/main/java/org/specdriven/agent/agent/ClaudeProvider.java
    - src/main/java/org/specdriven/agent/agent/ClaudeProviderFactory.java
    - src/main/java/org/specdriven/agent/agent/ContextWindowManager.java
    - src/main/java/org/specdriven/agent/agent/DefaultLlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/agent/LlmClient.java
    - src/main/java/org/specdriven/agent/agent/LlmConfig.java
    - src/main/java/org/specdriven/agent/agent/LlmProvider.java
    - src/main/java/org/specdriven/agent/agent/LlmProviderFactory.java
    - src/main/java/org/specdriven/agent/agent/LlmProviderRegistry.java
    - src/main/java/org/specdriven/agent/agent/LlmRequest.java
    - src/main/java/org/specdriven/agent/agent/LlmResponse.java
    - src/main/java/org/specdriven/agent/agent/LlmStreamCallback.java
    - src/main/java/org/specdriven/agent/agent/LlmUsage.java
    - src/main/java/org/specdriven/agent/agent/OpenAiClient.java
    - src/main/java/org/specdriven/agent/agent/OpenAiProvider.java
    - src/main/java/org/specdriven/agent/agent/OpenAiProviderFactory.java
    - src/main/java/org/specdriven/agent/agent/SkillRoute.java
    - src/main/java/org/specdriven/agent/agent/SseParser.java
    - src/main/java/org/specdriven/agent/agent/TokenCounter.java
    - src/main/java/org/specdriven/agent/agent/ToolCall.java
    - src/main/java/org/specdriven/agent/agent/ToolMessage.java
    - src/main/java/org/specdriven/agent/agent/ToolSchema.java
    - src/main/java/org/specdriven/agent/json/JsonReader.java
    - src/main/java/org/specdriven/agent/json/JsonWriter.java
  tests:
    - src/test/java/org/specdriven/agent/agent/ClaudeClientTest.java
    - src/test/java/org/specdriven/agent/agent/ClaudeProviderTest.java
    - src/test/java/org/specdriven/agent/agent/ClaudeStreamingTest.java
    - src/test/java/org/specdriven/agent/agent/ContextWindowManagerTest.java
    - src/test/java/org/specdriven/agent/agent/DefaultLlmProviderRegistryTest.java
    - src/test/java/org/specdriven/agent/agent/LlmClientTest.java
    - src/test/java/org/specdriven/agent/agent/LlmConfigTest.java
    - src/test/java/org/specdriven/agent/agent/LlmProviderRegistryTest.java
    - src/test/java/org/specdriven/agent/agent/LlmRequestTest.java
    - src/test/java/org/specdriven/agent/agent/LlmResponseTest.java
    - src/test/java/org/specdriven/agent/agent/MessageTest.java
    - src/test/java/org/specdriven/agent/agent/OpenAiClientTest.java
    - src/test/java/org/specdriven/agent/agent/OpenAiProviderTest.java
    - src/test/java/org/specdriven/agent/agent/OpenAiStreamingTest.java
    - src/test/java/org/specdriven/agent/agent/SkillRouteTest.java
    - src/test/java/org/specdriven/agent/agent/SseParserTest.java
    - src/test/java/org/specdriven/agent/agent/TokenCounterTest.java
    - src/test/java/org/specdriven/agent/agent/ToolCallTest.java
    - src/test/java/org/specdriven/agent/agent/ToolSchemaTest.java
    - src/test/java/org/specdriven/agent/json/JsonReaderTest.java
    - src/test/java/org/specdriven/agent/json/JsonWriterTest.java
---

# llm-provider.md

## CHANGED Requirements

### Requirement: ToolCall carries call ID
`ToolCall` MUST include a `callId` field containing the LLM-assigned invocation ID (e.g. OpenAI `tool_calls[].id`, Claude `content[].id`). `callId` MAY be null when created outside an LLM response context.

### Requirement: ToolMessage carries tool call ID
`ToolMessage` MUST include a `toolCallId` field to correlate tool results with the originating tool call. `toolCallId` MAY be null. Providers MUST use `toolCallId` when serializing tool result messages (e.g. OpenAI `tool_call_id` field).

### Requirement: JSON utilities in shared package
The system MUST provide `JsonWriter` and `JsonReader` utility classes in package `org.specdriven.agent.json` for LLM provider request/response serialization without external JSON library dependencies.

## ADDED Requirements

### Requirement: OpenAI-Compatible Provider
The system MUST provide an `OpenAiProvider` implementing `LlmProvider` that creates clients capable of calling any OpenAI Chat Completions API endpoint.

#### Scenario: Create provider with config
- GIVEN a `LlmConfig` with `baseUrl`, `apiKey`, and `model`
- WHEN `new OpenAiProvider(config)` is constructed
- THEN it MUST return a provider whose `config()` returns the given config

#### Scenario: Create client
- GIVEN an `OpenAiProvider` instance
- WHEN `createClient()` is called
- THEN it MUST return an `LlmClient` instance

#### Scenario: Provider close
- GIVEN an `OpenAiProvider` instance
- WHEN `close()` is called
- THEN it MUST complete without error

### Requirement: OpenAI Chat Completions Request
The system MUST serialize `LlmRequest` into OpenAI Chat Completions API JSON format and send it via HTTP POST.

#### Scenario: Basic text chat
- GIVEN an `OpenAiClient` and an `LlmRequest` with user messages and no tools
- WHEN `chat(request)` is called
- THEN it MUST POST to `{baseUrl}/chat/completions` with `Authorization: Bearer {apiKey}` header
- AND the request body MUST contain `model`, `messages` array, `temperature`, and `max_tokens`

#### Scenario: Chat with system prompt
- GIVEN an `LlmRequest` with a non-null `systemPrompt`
- WHEN the request is serialized
- THEN the first element of the `messages` array MUST be `{"role": "system", "content": "<systemPrompt>"}`

#### Scenario: Chat with tools
- GIVEN an `LlmRequest` with non-empty `tools`
- WHEN the request is serialized
- THEN the request body MUST include a `tools` array with each tool as `{"type": "function", "function": {"name": "...", "description": "...", "parameters": {...}}}`

#### Scenario: ToolMessage serialization
- GIVEN a `ToolMessage` with `toolCallId`
- WHEN the message is serialized
- THEN it MUST produce `{"role": "tool", "content": "...", "tool_call_id": "<toolCallId>"}`, falling back to `toolName` if `toolCallId` is null

### Requirement: OpenAI Chat Completions Response
The system MUST parse OpenAI Chat Completions JSON responses into `LlmResponse`.

#### Scenario: Text response
- GIVEN an HTTP 200 response with body containing `choices[0].message.content` and no `tool_calls`
- WHEN the response is parsed
- THEN it MUST return `LlmResponse.TextResponse` with the content, usage, and finish_reason

#### Scenario: Tool call response
- GIVEN an HTTP 200 response with body containing `choices[0].message.tool_calls`
- WHEN the response is parsed
- THEN it MUST return `LlmResponse.ToolCallResponse` with a list of `ToolCall`, each with `toolName`, `parameters`, and `callId` mapped from `tool_calls[].id`

#### Scenario: Usage parsing
- GIVEN a response with `usage.prompt_tokens`, `usage.completion_tokens`, `usage.total_tokens`
- WHEN the response is parsed
- THEN it MUST create `LlmUsage` with matching field values

### Requirement: Retry on Transient Errors
The system MUST retry failed requests on transient HTTP errors with exponential backoff.

#### Scenario: Retry on 429
- GIVEN a request that receives HTTP 429 response
- WHEN `maxRetries` is configured as N
- THEN the client MUST retry up to N times before failing

#### Scenario: Retry on server error
- GIVEN a request that receives HTTP 500, 502, 503, or 504
- WHEN `maxRetries` is configured as N
- THEN the client MUST retry up to N times

#### Scenario: No retry on client error
- GIVEN a request that receives HTTP 400, 401, 403, or 404
- WHEN the response is received
- THEN the client MUST NOT retry and MUST throw an exception

#### Scenario: Retry-After header
- GIVEN a 429 response with `Retry-After` header
- WHEN the client retries
- THEN it MUST wait the number of seconds specified by the header before the next attempt

#### Scenario: Exponential backoff
- GIVEN a retryable failure without `Retry-After` header
- WHEN the client retries
- THEN the wait time MUST follow exponential backoff starting at 1 second (1s, 2s, 4s, ...)

### Requirement: OpenAI Provider Factory
The system MUST provide an `OpenAiProviderFactory` implementing `LlmProviderFactory` for registry integration.

#### Scenario: Factory creates provider
- GIVEN an `OpenAiProviderFactory` and a valid `LlmConfig`
- WHEN `create(config)` is called
- THEN it MUST return a new `OpenAiProvider` instance

### Requirement: Configurable Base URL
The system MUST support any OpenAI-compatible base URL for provider flexibility.

#### Scenario: Custom base URL
- GIVEN a `LlmConfig` with `baseUrl` set to "https://api.deepseek.com/v1"
- WHEN `chat(request)` is called
- THEN the client MUST POST to "https://api.deepseek.com/v1/chat/completions"

### Requirement: Claude Messages API Provider
The system MUST provide a `ClaudeProvider` implementing `LlmProvider` that creates clients capable of calling the Anthropic Claude Messages API.

#### Scenario: Create provider with config
- GIVEN a `LlmConfig` with `baseUrl`, `apiKey`, and `model`
- WHEN `new ClaudeProvider(config)` is constructed
- THEN it MUST return a provider whose `config()` returns the given config

#### Scenario: Create client
- GIVEN a `ClaudeProvider` instance
- WHEN `createClient()` is called
- THEN it MUST return an `LlmClient` instance

#### Scenario: Provider close
- GIVEN a `ClaudeProvider` instance
- WHEN `close()` is called
- THEN it MUST complete without error

### Requirement: Claude Messages API Request
The system MUST serialize `LlmRequest` into the Claude Messages API JSON format and send it via HTTP POST with the required authentication headers.

#### Scenario: Required headers
- GIVEN a `ClaudeClient` sending any request
- THEN the HTTP request MUST include `x-api-key: {apiKey}`, `anthropic-version: 2023-06-01`, and `Content-Type: application/json`

#### Scenario: System prompt as top-level field
- GIVEN an `LlmRequest` with a non-null `systemPrompt`
- WHEN the request is serialized
- THEN the JSON body MUST contain a top-level `"system"` string field with the system prompt value
- AND the `messages` array MUST NOT contain any entry with `"role": "system"`

#### Scenario: Basic text chat
- GIVEN a `ClaudeClient` and an `LlmRequest` with user messages
- WHEN `chat(request)` is called
- THEN it MUST POST to `{baseUrl}/messages` with the required headers
- AND the request body MUST contain `model`, `messages` array, `max_tokens`, and optionally `temperature`

#### Scenario: max_tokens always present
- GIVEN an `LlmRequest` with no explicit `maxTokens` set
- WHEN the request is serialized
- THEN the JSON body MUST include `max_tokens` with a default value of 4096

#### Scenario: Chat with tools
- GIVEN an `LlmRequest` with non-empty `tools`
- WHEN the request is serialized
- THEN the request body MUST include a `tools` array with each tool as `{"name": "...", "description": "...", "input_schema": {...}}`
- AND the tool entries MUST NOT contain a `type: function` wrapper

#### Scenario: Message role mapping
- GIVEN messages containing `UserMessage` and `AssistantMessage`
- WHEN the request is serialized
- THEN `UserMessage` MUST map to `{"role": "user", "content": "<text>"}`
- AND `AssistantMessage` MUST map to `{"role": "assistant", "content": "<text>"}`

#### Scenario: Tool result message mapping
- GIVEN a `ToolMessage` in the message list
- WHEN the request is serialized
- THEN it MUST map to `{"role": "user", "content": [{"type": "tool_result", "tool_use_id": "<toolCallId or toolName>", "content": "<content>"}]}`
- AND `tool_use_id` MUST use `toolCallId` when set, falling back to `toolName` if null

#### Scenario: Tool call response carries call ID
- GIVEN a Claude response with `content[].type == "tool_use"` blocks
- WHEN the response is parsed
- THEN each resulting `ToolCall` MUST have `callId` set from the block's `id` field

### Requirement: Claude Messages API Response
The system MUST parse Claude Messages API JSON responses into `LlmResponse`.

#### Scenario: Text response
- GIVEN an HTTP 200 response with a `content` array containing a block of `type: "text"`
- WHEN the response is parsed
- THEN it MUST return `LlmResponse.TextResponse` with the text content, usage, and stop_reason

#### Scenario: Tool call response
- GIVEN an HTTP 200 response with a `content` array containing one or more blocks of `type: "tool_use"`
- WHEN the response is parsed
- THEN it MUST return `LlmResponse.ToolCallResponse` with a list of `ToolCall` mapped from each block's `name` and `input` map

#### Scenario: Usage parsing
- GIVEN a response with `usage.input_tokens` and `usage.output_tokens`
- WHEN the response is parsed
- THEN it MUST create `LlmUsage` where `promptTokens()` equals `input_tokens`, `completionTokens()` equals `output_tokens`, and `totalTokens()` equals their sum

#### Scenario: Stop reason
- GIVEN a response with a `stop_reason` field
- WHEN the response is parsed
- THEN the `LlmResponse` `finishReason()` MUST equal the value of `stop_reason`

### Requirement: Retry on Transient Errors (Claude)
The system MUST retry failed Claude API requests on transient HTTP errors with exponential backoff, using the same policy as the OpenAI provider.

#### Scenario: Retry on 429
- GIVEN a request that receives HTTP 429 from the Claude API
- WHEN `maxRetries` is configured as N
- THEN the client MUST retry up to N times before failing

#### Scenario: Retry on server error
- GIVEN a request that receives HTTP 500, 502, 503, or 504
- WHEN `maxRetries` is configured as N
- THEN the client MUST retry up to N times

#### Scenario: No retry on client error
- GIVEN a request that receives HTTP 400, 401, 403, or 404
- WHEN the response is received
- THEN the client MUST NOT retry and MUST throw an exception

#### Scenario: Retry-After header
- GIVEN a 429 response with a `Retry-After` header
- WHEN the client retries
- THEN it MUST wait the number of seconds specified before the next attempt

#### Scenario: Exponential backoff
- GIVEN a retryable failure without `Retry-After` header
- WHEN the client retries
- THEN the wait time MUST follow exponential backoff starting at 1 second (1s, 2s, 4s, ...)

### Requirement: Claude Provider Factory
The system MUST provide a `ClaudeProviderFactory` implementing `LlmProviderFactory` for registry integration under the key `"claude"`.

#### Scenario: Factory creates provider
- GIVEN a `ClaudeProviderFactory` and a valid `LlmConfig`
- WHEN `create(config)` is called
- THEN it MUST return a new `ClaudeProvider` instance

#### Scenario: Registry key
- GIVEN a `DefaultLlmProviderRegistry` configured with a `ClaudeProviderFactory` under key `"claude"`
- WHEN `fromConfig` loads a provider config with `type: claude`
- THEN it MUST instantiate a `ClaudeProvider`

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

### Requirement: LlmProviderRegistry Interface
The system MUST define a `LlmProviderRegistry` interface that manages named `LlmProvider` instances.

#### Scenario: Register a provider
- GIVEN a `LlmProviderRegistry` instance
- WHEN `register(String name, LlmProvider provider)` is called with a non-null name and provider
- THEN the provider MUST be stored under the given name and retrievable via `provider(name)`

#### Scenario: Reject duplicate registration
- GIVEN a `LlmProviderRegistry` with a provider registered under name "openai"
- WHEN `register("openai", anotherProvider)` is called
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: Lookup provider by name
- GIVEN a `LlmProviderRegistry` with provider "openai" registered
- WHEN `provider("openai")` is called
- THEN it MUST return the registered `LlmProvider` instance

#### Scenario: Lookup unknown provider
- GIVEN a `LlmProviderRegistry`
- WHEN `provider("unknown")` is called for a name that is not registered
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: List provider names
- GIVEN a `LlmProviderRegistry` with providers "openai" and "claude" registered
- WHEN `providerNames()` is called
- THEN it MUST return a set containing exactly "openai" and "claude"

#### Scenario: Remove a provider
- GIVEN a `LlmProviderRegistry` with provider "openai" registered
- WHEN `remove("openai")` is called
- THEN subsequent `provider("openai")` MUST throw `IllegalArgumentException`

#### Scenario: Close registry
- GIVEN a `LlmProviderRegistry` with multiple providers registered
- WHEN `close()` is called
- THEN `close()` MUST be called on every registered provider and all internal maps MUST be cleared

### Requirement: Default Provider Fallback
The system MUST support a designated default provider used when no specific provider name is specified.

#### Scenario: Set default provider
- GIVEN a `LlmProviderRegistry` with provider "openai" registered
- WHEN `setDefault("openai")` is called
- THEN `defaultProvider()` MUST return the "openai" provider

#### Scenario: Default not set, first registered used
- GIVEN a `LlmProviderRegistry` with "deepseek" as the first registered provider and no explicit default
- WHEN `defaultProvider()` is called
- THEN it MUST return the first registered provider

#### Scenario: Empty registry
- GIVEN a `LlmProviderRegistry` with no providers registered
- WHEN `defaultProvider()` is called
- THEN it MUST throw `IllegalStateException`

#### Scenario: Default removed
- GIVEN a `LlmProviderRegistry` with default set to "openai"
- WHEN `remove("openai")` is called
- THEN `defaultProvider()` MUST fall back to the first remaining provider, or throw `IllegalStateException` if empty

### Requirement: SkillRoute Record
The system MUST define an immutable `SkillRoute` record mapping a skill name to a provider and optional model override.

#### Scenario: Route with model override
- GIVEN a `SkillRoute("claude", "claude-opus-4-6-20250514")`
- THEN `providerName()` MUST return "claude" and `modelOverride()` MUST return "claude-opus-4-6-20250514"

#### Scenario: Route without model override
- GIVEN a `SkillRoute("deepseek", null)`
- THEN `providerName()` MUST return "deepseek" and `modelOverride()` MUST return null

### Requirement: Skill-to-Provider Routing
The system MUST support mapping skill names to specific providers via skill routing.

#### Scenario: Route a known skill
- GIVEN a `LlmProviderRegistry` with skill routing: "code-review" → SkillRoute("claude", "claude-opus-4-6-20250514")
- WHEN `route("code-review")` is called
- THEN it MUST return SkillRoute("claude", "claude-opus-4-6-20250514")

#### Scenario: Route an unknown skill
- GIVEN a `LlmProviderRegistry` with no routing for "translate"
- WHEN `route("translate")` is called
- THEN it MUST return null

#### Scenario: Register skill routing
- GIVEN a `LlmProviderRegistry` instance
- WHEN `addSkillRoute(String skillName, SkillRoute route)` is called
- THEN subsequent `route(skillName)` MUST return the registered route

### Requirement: LlmProviderFactory Interface
The system MUST define a `LlmProviderFactory` functional interface for creating `LlmProvider` instances from `LlmConfig`.

#### Scenario: Create provider from config
- GIVEN an `LlmProviderFactory` implementation and a valid `LlmConfig`
- WHEN `create(config)` is called
- THEN it MUST return a new `LlmProvider` instance

### Requirement: Registry Configuration Loading
The system MUST support loading the registry from a `Config` instance.

#### Scenario: Load from config
- GIVEN a `Config` with `llm.providers` section containing named provider configs, `llm.default`, and `llm.skill-routing`
- WHEN `DefaultLlmProviderRegistry.fromConfig(config, factories)` is called with matching factories
- THEN all providers MUST be registered, default MUST be set, and skill routing MUST be populated

#### Scenario: Missing default in config
- GIVEN a `Config` with providers but no `llm.default` key
- WHEN `fromConfig` is called
- THEN the first provider in config order MUST be used as default

### Requirement: SSE Parser
The system MUST provide an `SseParser` utility that reads Server-Sent Events from an `InputStream` and emits parsed event lines.

#### Scenario: Parse data line
- GIVEN an `InputStream` containing `data: {"content":"hello"}\n\n`
- WHEN `SseParser` reads the stream
- THEN it MUST emit an event with data `{"content":"hello"}`

#### Scenario: Skip comment lines
- GIVEN an `InputStream` containing `: this is a comment\ndata: {"content":"x"}\n\n`
- WHEN `SseParser` reads the stream
- THEN it MUST skip the comment line and emit only the data event

#### Scenario: Empty data line
- GIVEN an `InputStream` containing `data: \n\n`
- WHEN `SseParser` reads the stream
- THEN it MUST skip the empty data event

### Requirement: OpenAI Streaming
The `OpenAiClient` MUST implement `chatStreaming(LlmRequest, LlmStreamCallback)` using SSE.

#### Scenario: Stream text tokens
- GIVEN an `OpenAiClient` and an `LlmRequest` with user messages
- WHEN `chatStreaming(request, callback)` is called
- THEN the request body MUST include `"stream": true`
- AND `callback.onToken(String)` MUST be called for each content delta received
- AND `callback.onComplete(LlmResponse.TextResponse)` MUST be called with the accumulated response

#### Scenario: Stream tool call deltas
- GIVEN a streaming response with `choices[0].delta.tool_calls` chunks
- WHEN the stream completes
- THEN `callback.onComplete()` MUST receive a `LlmResponse.ToolCallResponse` with accumulated tool calls
- AND each `ToolCall` MUST have `callId` set from the first chunk's `id` field

#### Scenario: Stream termination
- GIVEN a streaming response from OpenAI
- WHEN `data: [DONE]` is received
- THEN the stream MUST be treated as complete and `callback.onComplete()` MUST be called

#### Scenario: Usage in streaming response
- GIVEN a streaming response that includes `usage` in the final chunk (or `stream_options: {"include_usage": true}`)
- WHEN the response is complete
- THEN `callback.onComplete()` MUST include an `LlmUsage` with the reported token counts

### Requirement: Claude Streaming
The `ClaudeClient` MUST implement `chatStreaming(LlmRequest, LlmStreamCallback)` using SSE.

#### Scenario: Stream text tokens
- GIVEN a `ClaudeClient` and an `LlmRequest` with user messages
- WHEN `chatStreaming(request, callback)` is called
- THEN the request body MUST include `"stream": true`
- AND `callback.onToken(String)` MUST be called for each `content_block_delta` event with `delta.text`

#### Scenario: Stream tool call deltas
- GIVEN a streaming response with `content_block_start` of `type: "tool_use"` followed by `content_block_delta` with `delta.partial_json`
- WHEN the stream completes
- THEN `callback.onComplete()` MUST receive a `LlmResponse.ToolCallResponse` with accumulated tool calls
- AND each `ToolCall` MUST have `callId` set from the block's `id` field

#### Scenario: Stream events
- GIVEN a Claude streaming response
- WHEN `event: message_stop` is received
- THEN the stream MUST be treated as complete and `callback.onComplete()` MUST be called

#### Scenario: Usage from message_delta
- GIVEN a streaming response with `message_delta` event containing `usage.output_tokens`
- WHEN the response is complete
- THEN `callback.onComplete()` MUST include an `LlmUsage` with input tokens from `message_start` and output tokens from `message_delta`

### Requirement: Streaming Error Handling
Both streaming implementations MUST handle errors consistently via the callback.

#### Scenario: Connection failure before stream starts
- GIVEN a streaming request that fails to connect
- WHEN the connection error occurs
- THEN the same retry policy as synchronous requests MUST apply before calling `callback.onError()`

#### Scenario: Error mid-stream
- GIVEN a streaming response that fails after delivering some tokens
- WHEN the error occurs (network disconnect, HTTP error mid-stream)
- THEN `callback.onError(Exception)` MUST be called immediately
- AND no retry MUST be attempted
- AND `callback.onComplete()` MUST NOT be called

#### Scenario: Provider API error in stream
- GIVEN a streaming response containing an error event (e.g., OpenAI error JSON, Claude `error` event type)
- WHEN the error event is parsed
- THEN `callback.onError(Exception)` MUST be called with a descriptive message

### Requirement: Token Counter
The system MUST provide a `TokenCounter` utility for token estimation.

#### Scenario: Estimate tokens for text
- GIVEN a non-null string
- WHEN `TokenCounter.estimate(String text)` is called
- THEN it MUST return a non-negative integer token count using jtokkit encoding

#### Scenario: Estimate tokens for messages
- GIVEN a list of `Message` instances
- WHEN `TokenCounter.estimate(List<Message> messages)` is called
- THEN it MUST return the sum of estimated tokens for all message content

#### Scenario: Empty input
- GIVEN an empty string or empty list
- WHEN `TokenCounter.estimate()` is called
- THEN it MUST return 0

### Requirement: Context Window Manager
The system MUST provide a `ContextWindowManager` that tracks token usage against a model's context window limit.

#### Scenario: Create with model limit
- GIVEN a model name and its context window size (e.g., 128000)
- WHEN `new ContextWindowManager(modelName, maxTokens)` is constructed
- THEN it MUST be ready to track usage against that limit

#### Scenario: Add usage
- GIVEN a `ContextWindowManager` with max 128000 tokens
- WHEN `addUsage(LlmUsage usage)` is called with `promptTokens=1000, completionTokens=500`
- THEN the tracked total MUST reflect the added usage

#### Scenario: Check remaining capacity
- GIVEN a `ContextWindowManager` with max 128000 tokens and tracked usage of 100000
- WHEN `remainingCapacity()` is called
- THEN it MUST return 28000

#### Scenario: Check if request fits
- GIVEN a `ContextWindowManager` with max 128000 tokens and tracked usage of 120000
- WHEN `canFit(int estimatedTokens)` is called with 10000
- THEN it MUST return true
- WHEN `canFit(10000)` is called with tracked usage of 125000
- THEN it MUST return false

#### Scenario: Reset tracking
- GIVEN a `ContextWindowManager` with tracked usage
- WHEN `reset()` is called
- THEN subsequent `remainingCapacity()` MUST return the full context window size

### Requirement: Streaming Retry Policy
Streaming requests MUST apply the same retry policy as synchronous requests for connection-level failures before the stream starts.

#### Scenario: Retry on pre-stream 429
- GIVEN a streaming request that receives HTTP 429 before any SSE data
- WHEN `maxRetries` is configured as N
- THEN the client MUST retry up to N times before calling `callback.onError()`

#### Scenario: No retry after first token
- GIVEN a streaming response that has delivered at least one token via `onToken()`
- WHEN a connection error occurs
- THEN `callback.onError()` MUST be called immediately with no retry

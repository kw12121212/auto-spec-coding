# llm-provider.md (delta)

## ADDED Requirements

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

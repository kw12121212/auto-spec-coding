# llm-provider.md (delta)

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

#### Scenario: Message role mapping
- GIVEN messages containing `UserMessage`, `AssistantMessage`, `ToolMessage`, and `SystemMessage`
- WHEN the request is serialized
- THEN each message MUST be mapped to `{"role": "<role()>", "content": "<content()>"}` with the correct role string

### Requirement: OpenAI Chat Completions Response
The system MUST parse OpenAI Chat Completions JSON responses into `LlmResponse`.

#### Scenario: Text response
- GIVEN an HTTP 200 response with body containing `choices[0].message.content` and no `tool_calls`
- WHEN the response is parsed
- THEN it MUST return `LlmResponse.TextResponse` with the content, usage, and finish_reason

#### Scenario: Tool call response
- GIVEN an HTTP 200 response with body containing `choices[0].message.tool_calls`
- WHEN the response is parsed
- THEN it MUST return `LlmResponse.ToolCallResponse` with a list of `ToolCall` mapped from `tool_calls[].function.name` and `tool_calls[].function.arguments` (parsed as JSON map)

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

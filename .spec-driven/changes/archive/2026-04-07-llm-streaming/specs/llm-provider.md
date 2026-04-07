# llm-provider.md (delta)

## ADDED Requirements

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

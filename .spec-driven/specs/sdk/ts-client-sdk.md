---
mapping:
  implementation:
    - sdk/ts/package.json
    - sdk/ts/tsconfig.json
    - sdk/ts/tsconfig.build.json
    - sdk/ts/tsconfig.lint.json
    - sdk/ts/eslint.config.js
    - sdk/ts/.prettierrc
    - sdk/ts/src/index.ts
    - sdk/ts/src/client.ts
    - sdk/ts/src/agent.ts
    - sdk/ts/src/events.ts
    - sdk/ts/src/models.ts
    - sdk/ts/src/tools.ts
    - sdk/ts/src/errors.ts
    - sdk/ts/src/retry.ts
  tests:
    - sdk/ts/src/client.test.ts
    - sdk/ts/src/agent.test.ts
    - sdk/ts/src/events.test.ts
    - sdk/ts/src/models.test.ts
    - sdk/ts/src/tools.test.ts
    - sdk/ts/src/errors.test.ts
    - sdk/ts/src/retry.test.ts
    - sdk/ts/src/integration.test.ts
---

# ts-client-sdk.md

## Requirements

### Requirement: TypeScript SDK HTTP client module

The system MUST provide a TypeScript npm package at `sdk/ts/` that allows TypeScript/Node.js callers to call the Java backend through the HTTP REST API.

#### Scenario: Client can be constructed with base URL
- GIVEN a valid HTTP API base URL
- WHEN a TypeScript caller constructs a `SpecDrivenClient` with that base URL
- THEN the client MUST be ready to send requests under `/api/v1`

#### Scenario: Client rejects missing base URL
- GIVEN an empty or missing base URL
- WHEN a TypeScript caller constructs a `SpecDrivenClient`
- THEN construction MUST throw with a validation error

#### Scenario: Client accepts injected fetch implementation
- GIVEN a caller-provided `fetch` implementation
- WHEN the client sends a request
- THEN the request MUST use the caller-provided `fetch` rather than the global

### Requirement: TypeScript SDK authentication headers

The TypeScript client SDK MUST support the authentication headers accepted by the Java HTTP API.

#### Scenario: Bearer token authentication
- GIVEN a client configured with a bearer token
- WHEN it sends an authenticated request
- THEN the request MUST include `Authorization: Bearer <token>`

#### Scenario: API key authentication
- GIVEN a client configured with an API key
- WHEN it sends an authenticated request
- THEN the request MUST include `X-API-Key: <key>`

#### Scenario: Health request may be sent without authentication
- GIVEN a client without credentials
- WHEN it calls the health endpoint
- THEN the request MUST be sent without an authentication header

### Requirement: TypeScript SDK HTTP API models

The TypeScript client SDK MUST expose TypeScript interfaces matching the existing HTTP REST API request and response payloads.

#### Scenario: Run agent request encodes supported fields
- GIVEN a run request with prompt, systemPrompt, maxTurns, and toolTimeoutSeconds
- WHEN the client sends the request
- THEN the JSON body MUST contain those fields using the Java HTTP API field names

#### Scenario: Run agent response decodes supported fields
- GIVEN a Java HTTP API run response containing `agentId`, `output`, and `state`
- WHEN the client decodes the response
- THEN the TypeScript response MUST expose those values with correct types

#### Scenario: Agent state response decodes supported fields
- GIVEN a Java HTTP API state response containing `agentId`, `state`, `createdAt`, and `updatedAt`
- WHEN the client decodes the response
- THEN the TypeScript response MUST expose those values

#### Scenario: Tools response decodes tool metadata
- GIVEN a Java HTTP API tools response containing one or more tools
- WHEN the client decodes the response
- THEN the TypeScript response MUST expose each tool name, description, and parameter list

### Requirement: TypeScript SDK endpoint methods

The TypeScript client SDK MUST provide direct methods for the HTTP REST API endpoints.

#### Scenario: health() calls health endpoint
- GIVEN a configured client
- WHEN the caller invokes `health()`
- THEN the client MUST send `GET /api/v1/health`
- AND return the decoded health response

#### Scenario: listTools() calls tools endpoint
- GIVEN a configured client
- WHEN the caller invokes `listTools()`
- THEN the client MUST send `GET /api/v1/tools`
- AND return the decoded tools response

#### Scenario: runAgent() calls run endpoint
- GIVEN a configured client and a run request
- WHEN the caller invokes `runAgent(request)`
- THEN the client MUST send `POST /api/v1/agent/run`
- AND return the decoded run response

#### Scenario: stopAgent() calls stop endpoint
- GIVEN a configured client and a non-empty agent ID
- WHEN the caller invokes `stopAgent(agentId)`
- THEN the client MUST send `POST /api/v1/agent/stop?id=<agentId>`

#### Scenario: getAgentState() calls state endpoint
- GIVEN a configured client and a non-empty agent ID
- WHEN the caller invokes `getAgentState(agentId)`
- THEN the client MUST send `GET /api/v1/agent/state?id=<agentId>`
- AND return the decoded state response

### Requirement: TypeScript SDK remote tool registration models

The TypeScript client SDK MUST expose TypeScript interfaces matching the Java HTTP API remote tool registration request and response payloads.

#### Scenario: Registration request encodes metadata
- GIVEN a `RemoteToolRegistrationRequest` with name, description, parameters, and callbackUrl
- WHEN the client sends the registration request
- THEN the JSON body MUST contain those fields using the Java HTTP API field names

#### Scenario: Invocation request decodes metadata
- GIVEN a Java backend POST containing `toolName` and `parameters`
- WHEN `ToolCallbackHandler` decodes the request body
- THEN the decoded `RemoteToolInvocationRequest` MUST expose `toolName` and `parameters`

#### Scenario: Invocation response encodes success
- GIVEN a local `ToolHandler` that returns a string output
- WHEN `ToolCallbackHandler` encodes the response
- THEN the JSON body MUST contain `success: true` and the handler output in `output`

#### Scenario: Invocation response encodes error
- GIVEN a local `ToolHandler` that throws an error
- WHEN `ToolCallbackHandler` encodes the response
- THEN the JSON body MUST contain `success: false` and the error message in `error`

### Requirement: TypeScript SDK registerRemoteTool method

The `SpecDrivenClient` MUST expose a `registerRemoteTool(request)` method that registers a callback-backed remote tool with the Java backend.

#### Scenario: registerRemoteTool sends registration request
- GIVEN a configured client and a `RemoteToolRegistrationRequest` with a non-empty name and callbackUrl
- WHEN the caller invokes `registerRemoteTool(request)`
- THEN the client MUST send `POST /api/v1/tools/register` with the registration payload
- AND return the decoded `ToolRegistrationResult`

#### Scenario: registerRemoteTool rejects missing name
- GIVEN a `RemoteToolRegistrationRequest` with an empty name
- WHEN the caller invokes `registerRemoteTool(request)`
- THEN the method MUST throw before sending any request

#### Scenario: registerRemoteTool rejects missing callbackUrl
- GIVEN a `RemoteToolRegistrationRequest` with an empty callbackUrl
- WHEN the caller invokes `registerRemoteTool(request)`
- THEN the method MUST throw before sending any request

### Requirement: TypeScript SDK tool callback handler

The TypeScript SDK MUST provide a `ToolCallbackHandler` class that dispatches Java backend tool invocation callbacks to locally registered `ToolHandler` functions.

#### Scenario: handleRequest invokes registered handler
- GIVEN a `ToolCallbackHandler` with a handler registered under name `lookup`
- WHEN the Java backend sends a POST with `{ toolName: "lookup", parameters: { q: "x" } }`
- THEN `ToolCallbackHandler` MUST call the registered handler with `{ q: "x" }`
- AND respond with `{ success: true, output: "<handler result>" }`

#### Scenario: handleRequest rejects unknown tool
- GIVEN a `ToolCallbackHandler` without a handler named `missing`
- WHEN the Java backend sends a POST for `missing`
- THEN `ToolCallbackHandler` MUST respond with `{ success: false, error: "<message>" }`

#### Scenario: handleRequest rejects non-POST method
- GIVEN a `ToolCallbackHandler`
- WHEN the Java backend sends a GET request
- THEN `ToolCallbackHandler` MUST respond with HTTP 405 and a tool error response

#### Scenario: handleRequest rejects invalid JSON
- GIVEN a `ToolCallbackHandler`
- WHEN the Java backend sends a POST with malformed JSON
- THEN `ToolCallbackHandler` MUST respond with HTTP 400 and a tool error response

#### Scenario: handleRequest preserves handler error
- GIVEN a registered handler that throws an error with message "not found"
- WHEN the Java backend invokes that tool
- THEN `ToolCallbackHandler` MUST respond with `{ success: false, error: "not found" }`

### Requirement: TypeScript SDK ToolCallbackHandler registration

The `ToolCallbackHandler` MUST allow callers to register and replace named local tool handlers.

#### Scenario: register stores handler
- GIVEN a `ToolCallbackHandler`
- WHEN the caller calls `register(name, handler)`
- THEN subsequent invocations for that name MUST call the registered handler

#### Scenario: register replaces existing handler
- GIVEN a `ToolCallbackHandler` with a handler already registered for `lookup`
- WHEN the caller calls `register("lookup", replacementHandler)`
- THEN subsequent invocations for `lookup` MUST call `replacementHandler`

#### Scenario: register rejects empty name
- GIVEN a `ToolCallbackHandler`
- WHEN the caller calls `register("", handler)`
- THEN `register` MUST throw with a validation error

#### Scenario: register rejects missing handler
- GIVEN a `ToolCallbackHandler`
- WHEN the caller calls `register("tool", null)`
- THEN `register` MUST throw with a validation error

### Requirement: TypeScript SDK event polling method

The TypeScript client SDK MUST provide a low-level method for polling backend events through `GET /api/v1/events` and MAY expose higher-level polling-backed subscription helpers built on that method without changing its existing observable request/response behavior.

#### Scenario: pollEvents() sends cursor and limit
- GIVEN a configured client and poll options with an `after` cursor and limit
- WHEN the caller invokes `pollEvents(options)`
- THEN the client MUST send `GET /api/v1/events?after=<cursor>&limit=<limit>`
- AND return the decoded event polling response

#### Scenario: pollEvents() sends type filter
- GIVEN a configured client and an event type filter
- WHEN the caller invokes `pollEvents({ type })`
- THEN the client MUST include the requested event type in the query

#### Scenario: pollEvents() uses authentication
- GIVEN a client configured with HTTP API credentials
- WHEN the caller polls events
- THEN the request MUST include the same authentication headers used by other authenticated client methods

### Requirement: TypeScript SDK event subscription helper

The TypeScript client SDK MUST provide a polling-backed event subscription helper for Node.js callers that continuously consumes backend events through the existing `GET /api/v1/events` endpoint.

#### Scenario: Subscription starts from current cursor
- GIVEN a configured client and a caller-created event subscription
- WHEN the caller starts consuming events without providing an explicit cursor
- THEN the subscription MUST begin polling through the existing `pollEvents()` client behavior
- AND each received event MUST preserve the backend event payload fields already exposed by the SDK

#### Scenario: Subscription resumes from explicit cursor
- GIVEN a configured client and a caller-created event subscription with an explicit `after` cursor
- WHEN the subscription performs its first poll
- THEN the first request MUST use that cursor value
- AND later polls MUST continue from the most recent returned `nextCursor`

#### Scenario: Empty poll result advances without emitting events
- GIVEN a running subscription and a poll result with no events
- WHEN the backend returns an empty `events` array and a reusable `nextCursor`
- THEN the subscription MUST not emit synthetic events
- AND the subscription MUST continue polling from the returned cursor

#### Scenario: Subscription stops cleanly
- GIVEN a running subscription
- WHEN the caller stops or closes it
- THEN no further polling requests MUST be issued for that subscription

### Requirement: TypeScript SDK event subscription controls

The TypeScript client SDK MUST let callers control polling cadence and lifecycle without reimplementing the poll loop themselves.

#### Scenario: Subscription uses configured polling interval
- GIVEN a caller configures a polling interval for a subscription
- WHEN the subscription runs
- THEN successive polls MUST wait for approximately that interval between completed polling cycles

#### Scenario: Subscription surfaces polling failure
- GIVEN a running subscription
- WHEN an underlying `pollEvents()` request fails
- THEN the subscription MUST surface that failure to the caller
- AND the subscription MUST stop continuing silently with an unknown state

### Requirement: TypeScript SDK typed error handling

The TypeScript client SDK MUST expose typed errors for HTTP API failures.

#### Scenario: API error response is wrapped in ApiError
- GIVEN the Java HTTP API returns a non-2xx response with an error JSON body
- WHEN the client receives the response
- THEN it MUST throw an `ApiError` exposing the HTTP status, API error code, and message

#### Scenario: Non-JSON error response is wrapped in ApiError
- GIVEN the server returns a non-2xx response without a valid error JSON body
- WHEN the client receives the response
- THEN it MUST throw an `ApiError` exposing the HTTP status

#### Scenario: Network error is retryable
- GIVEN the underlying fetch rejects with a network error
- WHEN the error propagates to the caller
- THEN the error MUST be marked retryable

#### Scenario: Client-side API errors are not retryable
- GIVEN the server returns HTTP 400, 401, 403, 404, or 422
- WHEN the client returns the error to the caller
- THEN the `ApiError` MUST be marked non-retryable

### Requirement: TypeScript SDK retry behavior

The TypeScript client SDK MUST retry only errors that are safe to retry under the HTTP API contract.

#### Scenario: Retry 429 response
- GIVEN the server returns HTTP 429 before a later success
- WHEN the caller invokes a client method with retries enabled
- THEN the client MUST retry the request and return the later success response

#### Scenario: Retry 5xx response
- GIVEN the server returns HTTP 500 before a later success
- WHEN the caller invokes a client method with retries enabled
- THEN the client MUST retry the request and return the later success response

#### Scenario: Do not retry validation error
- GIVEN the server returns HTTP 400
- WHEN the caller invokes a client method with retries enabled
- THEN the client MUST throw the error without retrying

#### Scenario: Retry budget is enforced
- GIVEN every attempt returns a retryable failure
- WHEN the retry budget is exhausted
- THEN the client MUST throw the last retryable error to the caller

### Requirement: TypeScript SDK agent wrapper

The TypeScript SDK MUST provide a `SpecDrivenAgent` class that wraps a `SpecDrivenClient` and exposes agent lifecycle operations through a typed, object-oriented interface.

#### Scenario: run() delegates to runAgent and returns AgentRunResult
- GIVEN a `SpecDrivenAgent` constructed with a client and optional `AgentConfig`
- WHEN the caller invokes `agent.run(prompt)`
- THEN the agent MUST call the client's `runAgent()` with the prompt merged with config defaults
- AND return an `AgentRunResult` containing `agentId`, `output`, and `state`

#### Scenario: run() forwards AgentConfig defaults in the request
- GIVEN an `AgentConfig` with `systemPrompt`, `maxTurns`, and `toolTimeoutSeconds`
- WHEN the caller invokes `agent.run(prompt)` without per-call overrides
- THEN the request sent to `runAgent()` MUST include those config values

#### Scenario: run() per-call options override AgentConfig defaults
- GIVEN an `AgentConfig` with default values and a `run()` call with per-call options
- WHEN the caller invokes `agent.run(prompt, options)`
- THEN the per-call options MUST take precedence over the `AgentConfig` defaults in the request

#### Scenario: stop() delegates agent ID to stopAgent
- GIVEN a `SpecDrivenAgent` and an agent ID returned from a prior `run()` call
- WHEN the caller invokes `agent.stop(agentId)`
- THEN the agent MUST call `client.stopAgent(agentId)` and return `void`

#### Scenario: getState() delegates agent ID to getAgentState
- GIVEN a `SpecDrivenAgent` and an agent ID
- WHEN the caller invokes `agent.getState(agentId)`
- THEN the agent MUST call `client.getAgentState(agentId)` and return the `AgentStateResponse`

### Requirement: TypeScript SDK agent factory method

The `SpecDrivenClient` MUST expose an `agent(config?)` factory method that returns a `SpecDrivenAgent` bound to the client.

#### Scenario: client.agent() returns bound SpecDrivenAgent
- GIVEN a constructed `SpecDrivenClient`
- WHEN the caller invokes `client.agent()` or `client.agent(config)`
- THEN the returned `SpecDrivenAgent` MUST use that client for all subsequent operations

### Requirement: TypeScript SDK hermetic integration coverage

The repository MUST provide a hermetic integration test suite for the TypeScript SDK that exercises the core client workflow against a mocked backend without requiring a live Java backend.

#### Scenario: Full client workflow succeeds against mocked backend
- GIVEN the TypeScript SDK integration test suite is running in the `sdk/ts/` workspace
- AND the backend HTTP surface is provided by a mocked test server
- WHEN the suite exercises `health()`, `listTools()`, `runAgent()`, `getAgentState()`, `stopAgent()`, and `pollEvents()`
- THEN the workflow MUST complete successfully without requiring a live Java backend process

#### Scenario: Integration suite verifies cursor progression
- GIVEN the mocked backend returns event polling cursors
- WHEN the integration suite polls events across multiple requests
- THEN the suite MUST verify that later requests continue from the returned cursor

#### Scenario: Integration suite verifies API error propagation
- GIVEN the mocked backend returns a typed API failure such as HTTP 400 or HTTP 404
- WHEN the integration suite invokes the corresponding SDK method
- THEN the suite MUST verify that the SDK surfaces an `ApiError` with the expected HTTP status and error metadata

#### Scenario: Integration suite remains hermetic
- GIVEN the integration suite runs with the mocked backend enabled
- WHEN the tests complete successfully
- THEN they MUST not require external network access or a separately started Java backend service

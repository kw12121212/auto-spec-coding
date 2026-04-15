---
mapping:
  implementation:
    - go-sdk/go.mod
    - go-sdk/specdriven/client.go
    - go-sdk/specdriven/agent.go
    - go-sdk/specdriven/models.go
    - go-sdk/specdriven/errors.go
    - go-sdk/specdriven/retry.go
    - go-sdk/specdriven/tools.go
    - go-sdk/specdriven/events.go
  tests:
    - go-sdk/specdriven/client_test.go
    - go-sdk/specdriven/agent_test.go
    - go-sdk/specdriven/models_test.go
    - go-sdk/specdriven/errors_test.go
    - go-sdk/specdriven/retry_test.go
    - go-sdk/specdriven/tools_test.go
    - go-sdk/specdriven/events_test.go
    - go-sdk/specdriven/integration_test.go
---

# go-client-sdk.md

## ADDED Requirements

### Requirement: Go SDK HTTP client module

The system MUST provide a Go client SDK module that allows Go callers to call the Java backend through the HTTP REST API.

#### Scenario: Client can be constructed with base URL
- GIVEN a valid HTTP API base URL
- WHEN a Go caller constructs a client with that base URL
- THEN the client MUST be ready to send requests under `/api/v1`

#### Scenario: Client rejects missing base URL
- GIVEN an empty base URL
- WHEN a Go caller constructs a client
- THEN construction MUST fail with a validation error

#### Scenario: Client uses caller-provided HTTP client
- GIVEN a caller-provided Go HTTP client
- WHEN the SDK client sends a request
- THEN the request MUST use the caller-provided HTTP client

### Requirement: Go SDK authentication headers

The Go client SDK MUST support the authentication headers accepted by the Java HTTP API.

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

### Requirement: Go SDK HTTP API models

The Go client SDK MUST expose Go types matching the existing HTTP REST API request and response payloads.

#### Scenario: Run agent request encodes supported fields
- GIVEN a run request with prompt, system prompt, max turns, and tool timeout seconds
- WHEN the client sends the request
- THEN the JSON body MUST contain those fields using the Java HTTP API field names

#### Scenario: Run agent response decodes supported fields
- GIVEN a Java HTTP API run response containing `agentId`, `output`, and `state`
- WHEN the client decodes the response
- THEN the Go response MUST expose those values

#### Scenario: Agent state response decodes supported fields
- GIVEN a Java HTTP API state response containing `agentId`, `state`, `createdAt`, and `updatedAt`
- WHEN the client decodes the response
- THEN the Go response MUST expose those values

#### Scenario: Tools response decodes tool metadata
- GIVEN a Java HTTP API tools response containing one or more tools
- WHEN the client decodes the response
- THEN the Go response MUST expose each tool name, description, and parameter list

### Requirement: Go SDK endpoint methods

The Go client SDK MUST provide direct methods for the existing HTTP REST API endpoints needed by the first Go SDK layer.

#### Scenario: Health method calls health endpoint
- GIVEN a configured client
- WHEN the caller invokes the health method
- THEN the client MUST send `GET /api/v1/health`
- AND return the decoded health response

#### Scenario: List tools method calls tools endpoint
- GIVEN a configured client
- WHEN the caller invokes the list tools method
- THEN the client MUST send `GET /api/v1/tools`
- AND return the decoded tools response

#### Scenario: Run agent method calls run endpoint
- GIVEN a configured client and a run request
- WHEN the caller invokes the run agent method
- THEN the client MUST send `POST /api/v1/agent/run`
- AND return the decoded run response

#### Scenario: Stop agent method calls stop endpoint
- GIVEN a configured client and an agent ID
- WHEN the caller invokes the stop agent method
- THEN the client MUST send `POST /api/v1/agent/stop?id=<agentId>`

#### Scenario: Get agent state method calls state endpoint
- GIVEN a configured client and an agent ID
- WHEN the caller invokes the get agent state method
- THEN the client MUST send `GET /api/v1/agent/state?id=<agentId>`
- AND return the decoded state response

### Requirement: Go SDK error handling

The Go client SDK MUST expose typed errors for HTTP API failures.

#### Scenario: API error response is preserved
- GIVEN the Java HTTP API returns a non-2xx response with an error JSON body
- WHEN the client receives the response
- THEN it MUST return a Go error exposing the HTTP status, API error code, and message

#### Scenario: Non-JSON error response is preserved
- GIVEN the server returns a non-2xx response without a valid error JSON body
- WHEN the client receives the response
- THEN it MUST return a Go error exposing the HTTP status

#### Scenario: Network error is retryable
- GIVEN the underlying HTTP client returns a network error
- WHEN the client returns the error to the caller
- THEN the error MUST be marked retryable

#### Scenario: Client-side API errors are not retryable
- GIVEN the server returns an HTTP 400, 401, 403, 404, or 422 response
- WHEN the client returns the error to the caller
- THEN the error MUST be marked non-retryable

### Requirement: Go SDK retry behavior

The Go client SDK MUST retry only errors that are safe to retry under the HTTP API contract.

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
- THEN the client MUST return the error without retrying the request

#### Scenario: Retry budget is enforced
- GIVEN every attempt returns a retryable failure
- WHEN the retry budget is exhausted
- THEN the client MUST return the last retryable error to the caller

### Requirement: Go SDK Agent facade

The Go client SDK MUST provide a high-level Agent API that wraps the existing HTTP client agent operations.

#### Scenario: Agent can be constructed from client
- GIVEN a configured Go SDK HTTP client
- WHEN a Go caller constructs an Agent handle from that client
- THEN the Agent handle MUST be ready to run prompts, stop agents, and query agent state through the Java backend HTTP REST API

#### Scenario: Agent rejects nil client
- GIVEN no Go SDK HTTP client
- WHEN a Go caller constructs an Agent handle
- THEN construction MUST fail with a validation error

### Requirement: Go SDK Agent run operation

The Go client SDK Agent API MUST allow Go callers to run a prompt through the Java backend agent run endpoint.

#### Scenario: Run sends prompt
- GIVEN an Agent handle
- WHEN the Go caller runs a prompt
- THEN the SDK MUST send that prompt using the existing run agent HTTP API contract
- AND return the decoded agent ID, output, and state to the caller

#### Scenario: Run sends optional parameters
- GIVEN an Agent handle and run options for system prompt, max turns, and tool timeout seconds
- WHEN the Go caller runs a prompt with those options
- THEN the SDK MUST include those values using the Java HTTP API field names

#### Scenario: Run rejects empty prompt
- GIVEN an Agent handle
- WHEN the Go caller runs an empty prompt
- THEN the SDK MUST fail before sending the request

#### Scenario: Run preserves API errors
- GIVEN the Java backend returns an API error response for a run request
- WHEN the Go caller runs a prompt through the Agent API
- THEN the returned error MUST preserve the typed API error details exposed by the existing HTTP client

### Requirement: Go SDK Agent stop operation

The Go client SDK Agent API MUST allow Go callers to stop an existing backend agent.

#### Scenario: Stop sends agent ID
- GIVEN an Agent handle and a non-empty agent ID
- WHEN the Go caller stops the agent
- THEN the SDK MUST call the existing stop agent HTTP API with that agent ID

#### Scenario: Stop rejects empty agent ID
- GIVEN an Agent handle
- WHEN the Go caller stops an agent with an empty agent ID
- THEN the SDK MUST fail before sending the request

### Requirement: Go SDK Agent state operation

The Go client SDK Agent API MUST allow Go callers to query backend agent state.

#### Scenario: State returns decoded response
- GIVEN an Agent handle and a non-empty agent ID
- WHEN the Go caller queries agent state
- THEN the SDK MUST return the decoded agent ID, state, createdAt, and updatedAt values

#### Scenario: State rejects empty agent ID
- GIVEN an Agent handle
- WHEN the Go caller queries state with an empty agent ID
- THEN the SDK MUST fail before sending the request

### Requirement: Go SDK Agent context handling

The Go client SDK Agent API MUST honor Go contexts for all backend operations.

#### Scenario: Run respects canceled context
- GIVEN an Agent handle and a canceled context
- WHEN the Go caller runs a prompt
- THEN the SDK MUST return a context cancellation error instead of a successful run result

#### Scenario: Stop and state respect canceled context
- GIVEN an Agent handle and a canceled context
- WHEN the Go caller stops an agent or queries agent state
- THEN the SDK MUST return a context cancellation error instead of a successful result

### Requirement: Go SDK tool registration models

The Go client SDK MUST expose Go types for callback-backed remote tool registration through the Java backend HTTP REST API.

#### Scenario: Tool registration encodes metadata
- GIVEN a Go tool registration with name, description, parameters, and callback URL
- WHEN the SDK sends the registration request
- THEN the JSON body MUST contain those fields using the Java HTTP API field names

#### Scenario: Tool registration rejects missing name
- GIVEN a Go tool registration with an empty name
- WHEN the caller registers the tool
- THEN the SDK MUST fail before sending the request

#### Scenario: Tool registration rejects missing callback URL
- GIVEN a Go tool registration with an empty callback URL
- WHEN the caller registers the tool
- THEN the SDK MUST fail before sending the request

### Requirement: Go SDK Tools facade

The Go client SDK MUST provide a high-level Tools API that wraps backend tool listing and callback-backed remote tool registration.

#### Scenario: Tools facade can be constructed from client
- GIVEN a configured Go SDK HTTP client
- WHEN a Go caller constructs a Tools handle from that client
- THEN the Tools handle MUST be ready to list backend tools and register callback-backed remote tools

#### Scenario: Tools facade rejects nil client
- GIVEN no Go SDK HTTP client
- WHEN a Go caller constructs a Tools handle
- THEN construction MUST fail with a validation error

#### Scenario: Tools list delegates to client
- GIVEN a Tools handle
- WHEN the Go caller lists tools
- THEN the SDK MUST call the existing Java backend tools list endpoint
- AND return the decoded tool metadata to the caller

#### Scenario: Register sends remote tool registration
- GIVEN a Tools handle and a valid remote tool registration
- WHEN the Go caller registers the tool
- THEN the SDK MUST send `POST /api/v1/tools/register`
- AND return the decoded registered tool metadata

#### Scenario: Register preserves API errors
- GIVEN the Java backend rejects a remote tool registration
- WHEN the Go caller registers the tool
- THEN the returned error MUST preserve the typed API error details exposed by the existing HTTP client

### Requirement: Go SDK tool callback handler

The Go client SDK MUST provide an HTTP handler that dispatches Java backend tool invocation callbacks to Go tool handlers registered in the local process.

#### Scenario: Callback invokes registered Go tool
- GIVEN a Go SDK callback handler with a registered tool handler named `lookup`
- WHEN the Java backend sends an invocation request for `lookup`
- THEN the handler MUST call the registered Go handler with the decoded parameters
- AND return a success response containing the handler output

#### Scenario: Callback rejects unknown tool
- GIVEN a Go SDK callback handler without a tool named `missing`
- WHEN the Java backend sends an invocation request for `missing`
- THEN the handler MUST return a tool error response

#### Scenario: Callback rejects invalid JSON
- GIVEN a Go SDK callback handler
- WHEN the Java backend sends an invalid JSON invocation request
- THEN the handler MUST return an error response instead of calling any tool handler

#### Scenario: Callback preserves handler error
- GIVEN a registered Go tool handler that returns an error
- WHEN the Java backend invokes that tool
- THEN the callback response MUST expose the handler error as a tool error

### Requirement: Go SDK tool context handling

The Go client SDK tool registration and callback handling MUST honor Go contexts.

#### Scenario: Register respects canceled context
- GIVEN a Tools handle and a canceled context
- WHEN the Go caller registers a remote tool
- THEN the SDK MUST return a context cancellation error instead of a successful registration

#### Scenario: Callback passes request context
- GIVEN a Go SDK callback handler receives an invocation request
- WHEN the registered Go tool handler is called
- THEN it MUST receive the incoming HTTP request context

### Requirement: Go SDK event models

The Go client SDK MUST expose Go types for event polling responses returned by the Java backend HTTP REST API.

#### Scenario: Event payload decodes supported fields
- GIVEN a Java HTTP API event response containing `sequence`, `type`, `timestamp`, `source`, and `metadata`
- WHEN the Go SDK decodes the response
- THEN the Go event value MUST expose those fields to the caller

#### Scenario: Empty event list decodes as empty slice
- GIVEN a Java HTTP API event response with no events
- WHEN the Go SDK decodes the response
- THEN the Go response MUST expose an empty event slice rather than nil

### Requirement: Go SDK event polling client

The Go client SDK MUST provide a low-level client method for polling backend events through `GET /api/v1/events`.

#### Scenario: Poll events sends cursor and limit
- GIVEN a configured Go SDK client and event poll options with an `after` cursor and limit
- WHEN the caller polls events
- THEN the SDK MUST send `GET /api/v1/events?after=<cursor>&limit=<limit>`
- AND return the decoded event polling response

#### Scenario: Poll events sends type filter
- GIVEN a configured Go SDK client and an event type filter
- WHEN the caller polls events
- THEN the SDK MUST include the requested event type in the event polling query

#### Scenario: Poll events uses authentication
- GIVEN a Go SDK client configured with HTTP API credentials
- WHEN the caller polls events
- THEN the request MUST include the same authentication headers used by other authenticated SDK methods

#### Scenario: Poll events preserves API errors
- GIVEN the Java backend rejects an event polling request
- WHEN the caller polls events
- THEN the returned error MUST preserve the typed API error details exposed by the existing HTTP client

### Requirement: Go SDK Events facade

The Go client SDK MUST provide a high-level Events API that wraps backend event polling.

#### Scenario: Events facade can be constructed from client
- GIVEN a configured Go SDK HTTP client
- WHEN a Go caller constructs an Events handle from that client
- THEN the Events handle MUST be ready to poll and subscribe to backend events

#### Scenario: Events facade rejects nil client
- GIVEN no Go SDK HTTP client
- WHEN a Go caller constructs an Events handle
- THEN construction MUST fail with a validation error

#### Scenario: Events poll delegates to client
- GIVEN an Events handle
- WHEN the Go caller polls events
- THEN the SDK MUST call the existing event polling client method
- AND return the decoded event polling response

### Requirement: Go SDK polling subscription

The Go client SDK MUST provide a polling subscription helper for receiving backend events until the caller stops it.

#### Scenario: Subscription delivers events in order
- GIVEN the backend returns multiple events across polling responses
- WHEN the caller starts a polling subscription
- THEN the SDK MUST deliver events to the caller callback in ascending sequence order

#### Scenario: Subscription advances cursor
- GIVEN the backend returns events with increasing sequence values
- WHEN the subscription performs its next poll
- THEN the SDK MUST use the latest delivered sequence as the next `after` cursor

#### Scenario: Subscription avoids duplicate delivery
- GIVEN a later backend polling response includes an event sequence that was already delivered
- WHEN the subscription processes that response
- THEN the SDK MUST NOT deliver the duplicate event again

#### Scenario: Subscription stops on context cancellation
- GIVEN a running polling subscription
- WHEN the caller cancels the context
- THEN the subscription MUST stop polling and return a context cancellation error or nil cancellation result according to its documented contract

#### Scenario: Subscription returns polling errors
- GIVEN the backend returns an API or transport error while a subscription is polling
- WHEN the error occurs
- THEN the subscription MUST return that error to the caller instead of silently dropping it

### Requirement: Go SDK integration tests
The Go client SDK MUST include integration tests that exercise the public client, agent, tools, and events workflows against an HTTP API compatible backend.

#### Scenario: Hermetic integration tests run by default
- GIVEN the Go SDK test suite is run without external backend environment variables
- WHEN the caller runs `go test ./...` from the Go SDK module
- THEN the integration tests MUST run against an in-process HTTP API compatible test server
- AND the tests MUST NOT require a live Java backend, network service, or credentials

#### Scenario: Integrated SDK workflows use one backend contract
- GIVEN an HTTP API compatible test server
- WHEN the integration tests exercise health, agent run/state/stop, tools list/register, and event polling through the Go SDK public APIs
- THEN the tests MUST verify the SDK sends the expected HTTP paths, methods, authentication headers, and JSON payloads
- AND the tests MUST verify the SDK decodes successful responses and typed API errors consistently across facades

### Requirement: Optional live backend validation
The Go client SDK MUST provide an optional integration test path for validating the SDK against a running Java HTTP backend.

#### Scenario: Live backend tests skip without configuration
- GIVEN `SPECDRIVEN_GO_SDK_BASE_URL` is unset
- WHEN the Go SDK test suite is run
- THEN live-backend integration tests MUST skip with a clear reason instead of failing

#### Scenario: Live backend tests use configured backend
- GIVEN `SPECDRIVEN_GO_SDK_BASE_URL` points to a running Java HTTP backend
- WHEN live-backend integration tests are run
- THEN the tests MUST construct the Go SDK client with that base URL
- AND verify non-destructive backend-compatible operations such as health and tool listing

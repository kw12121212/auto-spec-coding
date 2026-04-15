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
  tests:
    - go-sdk/specdriven/client_test.go
    - go-sdk/specdriven/agent_test.go
    - go-sdk/specdriven/models_test.go
    - go-sdk/specdriven/errors_test.go
    - go-sdk/specdriven/retry_test.go
    - go-sdk/specdriven/tools_test.go
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

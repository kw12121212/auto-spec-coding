---
mapping:
  implementation:
    - go-sdk/go.mod
    - go-sdk/specdriven/client.go
    - go-sdk/specdriven/models.go
    - go-sdk/specdriven/errors.go
    - go-sdk/specdriven/retry.go
  tests:
    - go-sdk/specdriven/client_test.go
    - go-sdk/specdriven/models_test.go
    - go-sdk/specdriven/errors_test.go
    - go-sdk/specdriven/retry_test.go
---

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

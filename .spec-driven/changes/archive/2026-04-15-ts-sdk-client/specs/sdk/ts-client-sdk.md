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
    - sdk/ts/src/models.ts
    - sdk/ts/src/errors.ts
    - sdk/ts/src/retry.ts
  tests:
    - sdk/ts/src/client.test.ts
    - sdk/ts/src/models.test.ts
    - sdk/ts/src/errors.test.ts
    - sdk/ts/src/retry.test.ts
    - sdk/ts/src/integration.test.ts
---

## ADDED Requirements

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

### Requirement: TypeScript SDK event polling method

The TypeScript client SDK MUST provide a low-level method for polling backend events through `GET /api/v1/events`.

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

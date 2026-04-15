---
mapping:
  implementation:
    - go-sdk/specdriven/client.go
    - go-sdk/specdriven/tools.go
  tests:
    - go-sdk/specdriven/tools_test.go
---

## ADDED Requirements

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

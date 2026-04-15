---
mapping:
  implementation:
    - go-sdk/specdriven/agent.go
  tests:
    - go-sdk/specdriven/agent_test.go
---

## ADDED Requirements

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

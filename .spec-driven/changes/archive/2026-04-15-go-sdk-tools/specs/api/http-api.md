---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/http/HttpApiServlet.java
    - src/main/java/org/specdriven/agent/http/HttpJsonCodec.java
    - src/main/java/org/specdriven/agent/http/RemoteToolRegistrationRequest.java
    - src/main/java/org/specdriven/agent/http/RemoteToolInvocationRequest.java
    - src/main/java/org/specdriven/agent/http/RemoteToolInvocationResponse.java
    - src/main/java/org/specdriven/agent/http/RemoteHttpTool.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
  tests:
    - src/test/java/org/specdriven/agent/http/HttpApiServletTest.java
    - src/test/java/org/specdriven/sdk/SdkAgentTest.java
---

## ADDED Requirements

### Requirement: Remote tool registration endpoint
The HTTP REST API MUST allow authenticated callers to register callback-backed remote tools that become available to backend agent runs.

#### Scenario: Register remote tool
- GIVEN a POST request to `/tools/register` with a tool name, description, parameter metadata, and callback URL
- WHEN the servlet processes the request
- THEN HTTP 200 MUST be returned
- AND the response MUST contain the registered tool metadata

#### Scenario: Registered remote tool appears in tools list
- GIVEN a remote tool was registered successfully
- WHEN a GET request is sent to `/tools`
- THEN the response MUST include that remote tool's name, description, and parameter metadata

#### Scenario: Missing tool name returns 400
- GIVEN a POST request to `/tools/register` with no tool name
- WHEN the servlet processes the request
- THEN an `ErrorResponse` with `status=400` and `error="invalid_params"` MUST be returned

#### Scenario: Missing callback URL returns 400
- GIVEN a POST request to `/tools/register` with no callback URL
- WHEN the servlet processes the request
- THEN an `ErrorResponse` with `status=400` and `error="invalid_params"` MUST be returned

#### Scenario: Built-in tool name cannot be overwritten
- GIVEN the SDK already has a built-in or builder-registered tool named `bash`
- WHEN a caller registers a remote tool named `bash`
- THEN an `ErrorResponse` with `status=409` and `error="conflict"` MUST be returned

#### Scenario: Remote tool registration is replaceable
- GIVEN a remote tool named `lookup` was registered previously
- WHEN a caller registers another remote tool named `lookup`
- THEN the later remote registration MUST replace the previous remote registration

### Requirement: Remote tool callback execution
Registered remote tools MUST be executable by backend agent runs through the normal tool execution path.

#### Scenario: Agent run invokes remote tool callback
- GIVEN a remote tool named `lookup` is registered with a callback URL
- AND an agent run produces a tool call for `lookup`
- WHEN the tool execution path executes that tool call
- THEN the system MUST send an invocation request to the registered callback URL
- AND a successful callback response MUST be converted into a successful tool result

#### Scenario: Callback tool error is returned as tool result error
- GIVEN a registered remote tool callback returns a tool error response
- WHEN the backend executes that remote tool
- THEN the tool execution MUST produce an error tool result
- AND the agent run MUST continue according to existing tool error feedback behavior

#### Scenario: Callback transport failure is returned as tool result error
- GIVEN a registered remote tool callback URL is unreachable
- WHEN the backend executes that remote tool
- THEN the tool execution MUST produce an error tool result
- AND the failure MUST NOT terminate the agent run outside existing tool error semantics

#### Scenario: Remote tool uses registered parameter metadata
- GIVEN a remote tool registration includes parameter metadata
- WHEN the tool is listed or exposed to the LLM tool schema
- THEN the system MUST use the registered parameter metadata for that remote tool

### Requirement: Remote tool HTTP API compatibility
Remote tool registration MUST preserve existing HTTP REST API behavior for existing routes.

#### Scenario: Existing tools list remains compatible
- GIVEN no remote tools are registered
- WHEN a GET request is sent to `/tools`
- THEN the response format MUST remain compatible with the existing `ToolsListResponse`

#### Scenario: Existing agent routes remain compatible
- GIVEN no remote tools are registered
- WHEN callers use `/agent/run`, `/agent/stop`, or `/agent/state`
- THEN those routes MUST keep their existing observable behavior

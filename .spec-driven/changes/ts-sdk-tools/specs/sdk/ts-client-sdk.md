---
mapping:
  implementation:
    - sdk/ts/src/tools.ts
    - sdk/ts/src/client.ts
    - sdk/ts/src/index.ts
  tests:
    - sdk/ts/src/tools.test.ts
---

## ADDED Requirements

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

#### Scenario: register rejects empty name
- GIVEN a `ToolCallbackHandler`
- WHEN the caller calls `register("", handler)`
- THEN `register` MUST throw with a validation error

#### Scenario: register rejects missing handler
- GIVEN a `ToolCallbackHandler`
- WHEN the caller calls `register("tool", null)`
- THEN `register` MUST throw with a validation error

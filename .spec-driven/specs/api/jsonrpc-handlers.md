# jsonrpc-handlers.md

## ADDED Requirements

### Requirement: JsonRpcDispatcher request routing

The system MUST provide a `JsonRpcDispatcher` class in `org.specdriven.agent.jsonrpc` implementing `JsonRpcMessageHandler` that routes inbound JSON-RPC requests to SDK operations.

#### Scenario: Dispatch known method
- GIVEN a `JsonRpcDispatcher` with an initialized SDK and transport
- WHEN `onRequest` is called with a `JsonRpcRequest` having `method="tools/list"`
- THEN the dispatcher MUST invoke the corresponding handler and send a `JsonRpcResponse` on the transport

#### Scenario: Unknown method returns methodNotFound
- GIVEN a `JsonRpcDispatcher` with an initialized SDK and transport
- WHEN `onRequest` is called with a `JsonRpcRequest` having `method="unknown/method"`
- THEN the dispatcher MUST send a `JsonRpcResponse` with error code `-32601` (Method not found)

### Requirement: initialize method

The dispatcher MUST handle the `initialize` method to bootstrap the SDK instance.

#### Scenario: Initialize with default config
- GIVEN a `JsonRpcDispatcher` with no prior initialization
- WHEN `onRequest` receives `method="initialize"` with empty params
- THEN the dispatcher MUST create a `SpecDriven` instance with default configuration
- AND respond with a result containing `version` (string) and `capabilities` (object)

#### Scenario: Initialize with config path
- GIVEN a valid YAML config file at `/path/to/config.yaml`
- WHEN `onRequest` receives `method="initialize"` with `params={"configPath": "/path/to/config.yaml"}`
- THEN the dispatcher MUST create a `SpecDriven` instance using `SpecDriven.builder().config(Path).build()`

#### Scenario: Initialize with system prompt
- GIVEN an initialize request with `params={"systemPrompt": "You are helpful"}`
- THEN the dispatcher MUST pass the system prompt to the SDK builder

#### Scenario: Double initialize rejected
- GIVEN an already initialized dispatcher
- WHEN `onRequest` receives `method="initialize"` again
- THEN the dispatcher MUST respond with error code `-32600` (Invalid Request)

### Requirement: shutdown method

The dispatcher MUST handle the `shutdown` method to release SDK resources.

#### Scenario: Shutdown cleans up
- GIVEN an initialized dispatcher
- WHEN `onRequest` receives `method="shutdown"`
- THEN the dispatcher MUST call `close()` on the `SpecDriven` instance
- AND respond with result `null`

#### Scenario: Operations after shutdown rejected
- GIVEN a shut-down dispatcher
- WHEN `onRequest` receives `method="agent/run"`
- THEN the dispatcher MUST respond with error code `-32600` (Invalid Request)

### Requirement: agent/run method

The dispatcher MUST handle the `agent/run` method to execute a prompt on a new agent.

#### Scenario: Run returns agent output
- GIVEN an initialized dispatcher
- WHEN `onRequest` receives `method="agent/run"` with `params={"prompt": "explain this code"}`
- THEN the dispatcher MUST create a new `SdkAgent` via `sdk.createAgent()`
- AND call `agent.run("explain this code")`
- AND respond with result `{"output": "<agent response>"}`

#### Scenario: Missing prompt rejected
- GIVEN an initialized dispatcher
- WHEN `onRequest` receives `method="agent/run"` with `params={}`
- THEN the dispatcher MUST respond with error code `-32602` (Invalid params)

#### Scenario: Agent error mapped to response
- GIVEN an initialized dispatcher where `agent.run()` throws `SdkLlmException`
- WHEN `onRequest` receives `method="agent/run"` with valid params
- THEN the dispatcher MUST respond with a JSON-RPC error, NOT throw

### Requirement: agent/stop method

The dispatcher MUST handle the `agent/stop` method to stop the currently running agent.

#### Scenario: Stop running agent
- GIVEN an initialized dispatcher with an in-flight `agent/run`
- WHEN `onRequest` receives `method="agent/stop"`
- THEN the dispatcher MUST call `stop()` on the running agent
- AND respond with result `null`

#### Scenario: Stop when no agent running
- GIVEN an initialized dispatcher with no in-flight run
- WHEN `onRequest` receives `method="agent/stop"`
- THEN the dispatcher MUST respond with result `null` (no-op)

### Requirement: agent/state method

The dispatcher MUST handle the `agent/state` method to query agent state.

#### Scenario: State returned
- GIVEN an initialized dispatcher
- WHEN `onRequest` receives `method="agent/state"`
- THEN the dispatcher MUST respond with result `{"state": "<AgentState name>"}`

### Requirement: tools/list method

The dispatcher MUST handle the `tools/list` method to enumerate registered tools.

#### Scenario: List tools
- GIVEN an initialized dispatcher with registered tools
- WHEN `onRequest` receives `method="tools/list"`
- THEN the dispatcher MUST respond with result containing a `tools` array
- AND each element MUST have `name` (string), `description` (string), and `parameters` (array)

### Requirement: $/cancel notification handling

The dispatcher MUST handle the `$/cancel` notification to cancel an in-flight request.

#### Scenario: Cancel in-flight request
- GIVEN a dispatcher with an in-flight `agent/run` for request ID 42
- WHEN `onNotification` receives `method="$/cancel"` with `params={"id": 42}`
- THEN the dispatcher MUST stop the agent associated with that request

#### Scenario: Cancel unknown ID is no-op
- GIVEN a dispatcher with no in-flight requests
- WHEN `onNotification` receives `method="$/cancel"` with `params={"id": 99}`
- THEN the dispatcher MUST NOT send any response or error

### Requirement: SDK exception to JSON-RPC error mapping

The dispatcher MUST map SDK exceptions to JSON-RPC error responses.

#### Scenario: SdkLlmException mapping
- GIVEN an agent run that throws `SdkLlmException`
- WHEN the dispatcher catches the exception
- THEN it MUST respond with error code `-32603` and `data` containing `{"retryable": true}`

#### Scenario: SdkPermissionException mapping
- GIVEN an operation that throws `SdkPermissionException`
- WHEN the dispatcher catches the exception
- THEN it MUST respond with error code `-32600`

#### Scenario: SdkToolException mapping
- GIVEN an operation that throws `SdkToolException`
- WHEN the dispatcher catches the exception
- THEN it MUST respond with error code `-32602`

#### Scenario: Unhandled exception mapping
- GIVEN an unexpected runtime exception during handler execution
- WHEN the dispatcher catches the exception
- THEN it MUST respond with error code `-32603` (Internal error)

### Requirement: Event forwarding as notifications

The dispatcher MUST forward SDK events to the client as JSON-RPC notifications.

#### Scenario: Agent event forwarded
- GIVEN an initialized dispatcher with event forwarding active
- WHEN the SDK emits an `Event` during agent execution
- THEN the dispatcher MUST send a `JsonRpcNotification` with `method="event"` and `params` containing `type`, `source`, and `metadata` from the event

#### Scenario: Event forwarding starts on initialize
- GIVEN a dispatcher handling `initialize`
- WHEN the SDK is bootstrapped
- THEN an `SdkEventListener` MUST be registered on the SDK to forward all events

### Requirement: Transport error handling

The dispatcher's `onError` method MUST handle transport errors gracefully.

#### Scenario: Transport error logged
- GIVEN a running dispatcher
- WHEN `onError` is called with a transport exception
- THEN the dispatcher MUST NOT throw
- AND the dispatcher SHOULD attempt to send an error notification to the client if the transport is still writable

---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcDispatcher.java
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcMessageHandler.java
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcTransport.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
  tests:
    - src/test/java/org/specdriven/agent/jsonrpc/JsonRpcDispatcherTest.java
---

# jsonrpc-handlers.md

## ADDED Requirements

### Requirement: JsonRpcDispatcher request routing

The system MUST provide a `JsonRpcDispatcher` class in `org.specdriven.agent.jsonrpc` implementing `JsonRpcMessageHandler` that routes inbound JSON-RPC requests to SDK operations, including `question/answer`, `workflow/start`, `workflow/state`, and `workflow/result`.

#### Scenario: Dispatch known method
- GIVEN a `JsonRpcDispatcher` with an initialized SDK and transport
- WHEN `onRequest` is called with a `JsonRpcRequest` having `method="tools/list"`
- THEN the dispatcher MUST invoke the corresponding handler and send a `JsonRpcResponse` on the transport

#### Scenario: Unknown method returns methodNotFound
- GIVEN a `JsonRpcDispatcher` with an initialized SDK and transport
- WHEN `onRequest` is called with a `JsonRpcRequest` having `method="unknown/method"`
- THEN the dispatcher MUST send a `JsonRpcResponse` with error code `-32601` (Method not found)

#### Scenario: Dispatch workflow/start method
- GIVEN a `JsonRpcDispatcher` with an initialized SDK and transport
- WHEN `onRequest` is called with a `JsonRpcRequest` having `method="workflow/start"`
- THEN the dispatcher MUST invoke the corresponding workflow-start handler and send a `JsonRpcResponse` on the transport

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

#### Scenario: Initialize response includes question/answer capability
- GIVEN a `JsonRpcDispatcher` handling `initialize`
- WHEN the response `capabilities.methods` list is returned
- THEN it MUST include `"question/answer"`

#### Scenario: Initialize response includes workflow capabilities
- GIVEN a `JsonRpcDispatcher` handling `initialize`
- WHEN the response `capabilities.methods` list is returned
- THEN it MUST include `"workflow/start"`
- AND it MUST include `"workflow/state"`
- AND it MUST include `"workflow/result"`

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

### Requirement: question/answer method

The dispatcher MUST handle the `question/answer` method to submit a human answer to a waiting question via JSON-RPC.

#### Scenario: Approve a waiting question
- GIVEN an initialized dispatcher with a question in `WAITING_FOR_ANSWER` status with `PAUSE_WAIT_HUMAN` delivery mode
- WHEN `onRequest` receives `method="question/answer"` with `params={"sessionId": "s1", "questionId": "q1", "approved": true}`
- THEN the dispatcher MUST construct an `Answer` with `source == HUMAN_INLINE` and `decision == ANSWER_ACCEPTED`
- AND submit the answer through `QuestionDeliveryService.submitReply()`
- AND respond with result `{"status": "accepted"}`

#### Scenario: Reject a waiting question
- GIVEN an initialized dispatcher with a question in `WAITING_FOR_ANSWER` status with `PAUSE_WAIT_HUMAN` delivery mode
- WHEN `onRequest` receives `method="question/answer"` with `params={"sessionId": "s1", "questionId": "q1", "approved": false}`
- THEN the dispatcher MUST construct an `Answer` with `source == HUMAN_INLINE` and `decision == CANCELLED`
- AND submit the answer through `QuestionDeliveryService.submitReply()`
- AND respond with result `{"status": "accepted"}`

#### Scenario: SDK not initialized
- GIVEN a `JsonRpcDispatcher` with no prior initialization
- WHEN `onRequest` receives `method="question/answer"`
- THEN the dispatcher MUST respond with error code `-32600` (Invalid Request)

#### Scenario: Missing sessionId parameter
- GIVEN an initialized dispatcher
- WHEN `onRequest` receives `method="question/answer"` with `params={"questionId": "q1", "approved": true}`
- THEN the dispatcher MUST respond with error code `-32602` (Invalid Params)

#### Scenario: Missing questionId parameter
- GIVEN an initialized dispatcher
- WHEN `onRequest` receives `method="question/answer"` with `params={"sessionId": "s1", "approved": true}`
- THEN the dispatcher MUST respond with error code `-32602` (Invalid Params)

#### Scenario: Missing approved parameter
- GIVEN an initialized dispatcher
- WHEN `onRequest` receives `method="question/answer"` with `params={"sessionId": "s1", "questionId": "q1"}`
- THEN the dispatcher MUST respond with error code `-32602` (Invalid Params)

#### Scenario: Question not found or expired
- GIVEN an initialized dispatcher with no waiting question for the given sessionId and questionId
- WHEN `onRequest` receives `method="question/answer"` with valid params
- THEN the dispatcher MUST respond with a JSON-RPC error indicating the question was not found or has expired

#### Scenario: Unsupported delivery mode
- GIVEN an initialized dispatcher with a question in `WAITING_FOR_ANSWER` status with `PUSH_MOBILE_WAIT_HUMAN` delivery mode
- WHEN `onRequest` receives `method="question/answer"` with matching sessionId and questionId
- THEN the dispatcher MUST respond with a JSON-RPC error indicating the delivery mode is not supported

### Requirement: question/answer Answer construction

The `question/answer` handler MUST construct an `Answer` from the `approved` boolean with the following field mapping.

#### Scenario: Answer fields for approval
- GIVEN `approved == true`
- WHEN the handler constructs an `Answer`
- THEN `source` MUST be `HUMAN_INLINE`
- AND `decision` MUST be `ANSWER_ACCEPTED`
- AND `confidence` MUST be `1.0`
- AND `content` MUST be `"Approved"`
- AND `basisSummary` MUST be `"Human inline response via JSON-RPC"`
- AND `sourceRef` MUST be `"json-rpc"`
- AND `deliveryMode` MUST match the waiting question's `deliveryMode`

#### Scenario: Answer fields for rejection
- GIVEN `approved == false`
- WHEN the handler constructs an `Answer`
- THEN `source` MUST be `HUMAN_INLINE`
- AND `decision` MUST be `CANCELLED`
- AND `confidence` MUST be `1.0`
- AND `content` MUST be `"Rejected"`
- AND `basisSummary` MUST be `"Human inline response via JSON-RPC"`
- AND `sourceRef` MUST be `"json-rpc"`
- AND `deliveryMode` MUST match the waiting question's `deliveryMode`

### Requirement: workflow/start method

The dispatcher MUST handle the `workflow/start` method to start a declared workflow by name.

#### Scenario: Start declared workflow by name
- GIVEN an initialized dispatcher
- AND a declared workflow named `invoice-approval`
- WHEN `onRequest` receives `method="workflow/start"` with `params={"workflowName": "invoice-approval", "input": {"invoiceId": "inv-1"}}`
- THEN the dispatcher MUST start that workflow through the SDK workflow runtime surface
- AND it MUST respond with a result containing a non-blank `workflowId`
- AND it MUST include `workflowName="invoice-approval"`
- AND it MUST include `status="ACCEPTED"`

#### Scenario: Missing workflowName is rejected
- GIVEN an initialized dispatcher
- WHEN `onRequest` receives `method="workflow/start"` with `params={}`
- THEN the dispatcher MUST respond with error code `-32602` (Invalid params)

#### Scenario: Unknown workflow name returns error
- GIVEN an initialized dispatcher
- AND no declared workflow matches the requested name
- WHEN `onRequest` receives `method="workflow/start"` with a `workflowName`
- THEN the dispatcher MUST respond with a JSON-RPC error indicating the workflow is not declared

### Requirement: workflow/state method

The dispatcher MUST handle the `workflow/state` method to query workflow instance state by ID.

#### Scenario: Query workflow state by ID
- GIVEN an initialized dispatcher and a previously started workflow instance
- WHEN `onRequest` receives `method="workflow/state"` with `params={"workflowId": "wf-123"}`
- THEN the dispatcher MUST respond with a result containing the same `workflowId`
- AND it MUST include the current workflow status

#### Scenario: Unknown workflow instance returns error
- GIVEN an initialized dispatcher
- AND no workflow instance exists for the requested `workflowId`
- WHEN `onRequest` receives `method="workflow/state"`
- THEN the dispatcher MUST respond with a JSON-RPC error indicating the workflow instance was not found

### Requirement: workflow/result method

The dispatcher MUST handle the `workflow/result` method to return the current workflow result view for a workflow instance.

#### Scenario: Non-terminal workflow result view is pending
- GIVEN an initialized dispatcher and a workflow instance whose status is not terminal
- WHEN `onRequest` receives `method="workflow/result"` with `params={"workflowId": "wf-123"}`
- THEN the dispatcher MUST respond with a result containing the current workflow status
- AND the final result payload MUST be null or empty

#### Scenario: Terminal workflow result view is returned
- GIVEN an initialized dispatcher and a workflow instance whose status is `SUCCEEDED`, `FAILED`, or `CANCELLED`
- WHEN `onRequest` receives `method="workflow/result"` with `params={"workflowId": "wf-123"}`
- THEN the dispatcher MUST respond with a result containing the terminal workflow status
- AND it MUST include either the final result payload or a failure summary consistent with that status

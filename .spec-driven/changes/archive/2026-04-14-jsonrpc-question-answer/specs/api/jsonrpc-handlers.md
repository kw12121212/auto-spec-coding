---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcDispatcher.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
  tests:
    - src/test/java/org/specdriven/agent/jsonrpc/JsonRpcDispatcherTest.java
---

## MODIFIED Requirements

### Requirement: JsonRpcDispatcher request routing
Previously: The system MUST provide a `JsonRpcDispatcher` class in `org.specdriven.agent.jsonrpc` implementing `JsonRpcMessageHandler` that routes inbound JSON-RPC requests to SDK operations.
The system MUST provide a `JsonRpcDispatcher` class in `org.specdriven.agent.jsonrpc` implementing `JsonRpcMessageHandler` that routes inbound JSON-RPC requests to SDK operations, including `question/answer`.

#### Scenario: Dispatch question/answer method
- GIVEN a `JsonRpcDispatcher` with an initialized SDK and transport
- WHEN `onRequest` is called with a `JsonRpcRequest` having `method="question/answer"`
- THEN the dispatcher MUST invoke the corresponding handler and send a `JsonRpcResponse` on the transport

### Requirement: initialize method

The dispatcher MUST handle the `initialize` method to bootstrap the SDK instance.

#### Scenario: Initialize response includes question/answer capability
- GIVEN a `JsonRpcDispatcher` handling `initialize`
- WHEN the response `capabilities.methods` list is returned
- THEN it MUST include `"question/answer"`

## ADDED Requirements

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

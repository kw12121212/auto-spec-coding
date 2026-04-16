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
Previously: The system MUST provide a `JsonRpcDispatcher` class in `org.specdriven.agent.jsonrpc` implementing `JsonRpcMessageHandler` that routes inbound JSON-RPC requests to SDK operations, including `question/answer`.
The system MUST provide a `JsonRpcDispatcher` class in `org.specdriven.agent.jsonrpc` implementing `JsonRpcMessageHandler` that routes inbound JSON-RPC requests to SDK operations, including `question/answer`, `workflow/start`, `workflow/state`, and `workflow/result`.

#### Scenario: Dispatch workflow/start method
- GIVEN a `JsonRpcDispatcher` with an initialized SDK and transport
- WHEN `onRequest` is called with a `JsonRpcRequest` having `method="workflow/start"`
- THEN the dispatcher MUST invoke the corresponding workflow-start handler and send a `JsonRpcResponse` on the transport

### Requirement: initialize method

The dispatcher MUST handle the `initialize` method to bootstrap the SDK instance.

#### Scenario: Initialize response includes workflow capabilities
- GIVEN a `JsonRpcDispatcher` handling `initialize`
- WHEN the response `capabilities.methods` list is returned
- THEN it MUST include `"workflow/start"`
- AND it MUST include `"workflow/state"`
- AND it MUST include `"workflow/result"`

## ADDED Requirements

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

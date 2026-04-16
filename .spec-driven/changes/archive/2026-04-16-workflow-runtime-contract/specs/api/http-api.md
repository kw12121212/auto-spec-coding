---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/http/HttpApiServlet.java
    - src/main/java/org/specdriven/agent/http/HttpJsonCodec.java
    - src/main/java/org/specdriven/agent/http/WorkflowStartRequest.java
    - src/main/java/org/specdriven/agent/http/WorkflowInstanceResponse.java
    - src/main/java/org/specdriven/agent/http/WorkflowResultResponse.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
  tests:
    - src/test/java/org/specdriven/agent/http/HttpApiServletTest.java
    - src/test/java/org/specdriven/agent/http/HttpModelsTest.java
---

## ADDED Requirements

### Requirement: Route dispatching — workflow runtime

The servlet MUST additionally dispatch workflow runtime routes under `/api/v1/workflows`.

#### Scenario: POST /workflows routes to workflow start handler
- GIVEN a POST request with `pathInfo="/workflows"`
- WHEN `service()` is called
- THEN the request MUST be routed to the workflow start handler

#### Scenario: GET /workflows/{workflowId} routes to workflow state handler
- GIVEN a GET request with `pathInfo="/workflows/wf-123"`
- WHEN `service()` is called
- THEN the request MUST be routed to the workflow state handler

#### Scenario: GET /workflows/{workflowId}/result routes to workflow result handler
- GIVEN a GET request with `pathInfo="/workflows/wf-123/result"`
- WHEN `service()` is called
- THEN the request MUST be routed to the workflow result handler

### Requirement: Workflow start handler

The HTTP REST API MUST allow an authenticated caller to start a declared workflow by name.

#### Scenario: Start declared workflow over HTTP
- GIVEN a declared workflow named `invoice-approval`
- WHEN an authenticated caller sends `POST /api/v1/workflows` with body `{"workflowName":"invoice-approval","input":{"invoiceId":"inv-1"}}`
- THEN the response MUST return HTTP 202
- AND the response body MUST include a non-blank `workflowId`
- AND it MUST include `workflowName="invoice-approval"`
- AND it MUST include `status="ACCEPTED"`

#### Scenario: Missing workflow name returns 400
- GIVEN an authenticated caller sends `POST /api/v1/workflows` with a body that does not include `workflowName`
- WHEN the request is processed
- THEN an `ErrorResponse` with `status=400` and `error="invalid_params"` MUST be returned

#### Scenario: Unknown workflow name returns 404
- GIVEN no declared workflow matches the requested `workflowName`
- WHEN an authenticated caller sends `POST /api/v1/workflows`
- THEN an `ErrorResponse` with `status=404` and `error="not_found"` MUST be returned

### Requirement: Workflow state handler

The HTTP REST API MUST expose workflow instance state query by workflow ID.

#### Scenario: Query workflow state over HTTP
- GIVEN a previously started workflow instance
- WHEN an authenticated caller sends `GET /api/v1/workflows/{workflowId}`
- THEN the response MUST return HTTP 200
- AND the response body MUST include the same `workflowId`
- AND it MUST include the workflow name and current status

#### Scenario: Unknown workflow instance returns 404
- GIVEN no workflow instance exists for the requested `workflowId`
- WHEN an authenticated caller sends `GET /api/v1/workflows/{workflowId}`
- THEN an `ErrorResponse` with `status=404` and `error="not_found"` MUST be returned

### Requirement: Workflow result handler

The HTTP REST API MUST expose the current workflow result view for a workflow instance.

#### Scenario: Non-terminal workflow result view is pending
- GIVEN a previously started workflow instance whose status is not terminal
- WHEN an authenticated caller sends `GET /api/v1/workflows/{workflowId}/result`
- THEN the response MUST return HTTP 200
- AND the response body MUST include the current workflow status
- AND the final result payload MUST be null or empty

#### Scenario: Successful workflow result view includes final result
- GIVEN a previously started workflow instance whose status is `SUCCEEDED`
- WHEN an authenticated caller sends `GET /api/v1/workflows/{workflowId}/result`
- THEN the response MUST return HTTP 200
- AND the response body MUST include `status="SUCCEEDED"`
- AND it MUST include the final workflow result payload

#### Scenario: Failed workflow result view includes failure summary
- GIVEN a previously started workflow instance whose status is `FAILED` or `CANCELLED`
- WHEN an authenticated caller sends `GET /api/v1/workflows/{workflowId}/result`
- THEN the response MUST return HTTP 200
- AND the response body MUST include the terminal workflow status
- AND it MUST expose a failure or cancellation summary instead of a successful result payload

### Requirement: Workflow HTTP API compatibility

The workflow runtime routes MUST preserve existing HTTP REST API behavior for existing routes.

#### Scenario: Existing agent routes remain compatible
- GIVEN callers use existing `/health`, `/tools`, `/events`, `/agent/*`, `/platform/health`, `/callbacks/{channel}`, or `/delivery/status/{questionId}` routes
- WHEN workflow runtime routes are available
- THEN those existing routes MUST keep their existing observable behavior

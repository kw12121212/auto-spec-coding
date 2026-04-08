# http-routes.md

## ADDED Requirements

### Requirement: HTTP route servlet

The system MUST provide `HttpApiServlet` in `org.specdriven.agent.http` extending `jakarta.servlet.http.HttpServlet`, registered on `/api/v1/*`.

#### Scenario: Servlet initializes with SDK
- GIVEN no pre-configured SDK
- WHEN `init()` is called
- THEN a default `SpecDriven` SDK instance MUST be created via `SpecDriven.builder().build()`

#### Scenario: Servlet accepts injected SDK
- GIVEN a `SpecDriven` SDK instance
- WHEN `HttpApiServlet(sdk)` is constructed
- THEN `init()` MUST use the injected SDK instead of creating a new one

### Requirement: Route dispatching

The servlet MUST dispatch HTTP requests by parsing `getPathInfo()` segments and `getMethod()`.

#### Scenario: GET /health routes to health handler
- GIVEN a GET request with `pathInfo="/health"`
- WHEN `service()` is called
- THEN a `HealthResponse` with `status="ok"` MUST be returned with HTTP 200

#### Scenario: GET /tools routes to tools handler
- GIVEN a GET request with `pathInfo="/tools"`
- WHEN `service()` is called
- THEN a `ToolsListResponse` MUST be returned with HTTP 200

#### Scenario: POST /agent/run routes to agent run handler
- GIVEN a POST request with `pathInfo="/agent/run"`
- WHEN `service()` is called
- THEN a `RunAgentResponse` MUST be returned with HTTP 200

#### Scenario: POST /agent/stop routes to agent stop handler
- GIVEN a POST request with `pathInfo="/agent/stop"` and query param `id`
- WHEN `service()` is called
- THEN HTTP 200 MUST be returned

#### Scenario: GET /agent/state routes to agent state handler
- GIVEN a GET request with `pathInfo="/agent/state"` and query param `id`
- WHEN `service()` is called
- THEN an `AgentStateResponse` MUST be returned with HTTP 200

#### Scenario: Unknown route returns 404
- GIVEN any request with an unrecognized `pathInfo`
- WHEN `service()` is called
- THEN an `ErrorResponse` with `status=404` and `error="not_found"` MUST be returned

#### Scenario: Wrong HTTP method returns 405
- GIVEN a request whose method does not match the route's expected method
- WHEN `service()` is called
- THEN an `ErrorResponse` with `status=405` and `error="method_not_allowed"` MUST be returned

### Requirement: Agent run handler

The `POST /agent/run` handler MUST decode the request body as `RunAgentRequest` via `HttpJsonCodec`, create a new `SdkAgent`, call `run(prompt)`, and encode a `RunAgentResponse`.

#### Scenario: Valid request returns agent result
- GIVEN a POST request with JSON body `{"prompt":"hello"}`
- WHEN the handler processes the request
- THEN a `RunAgentResponse` with a generated `agentId`, the agent output, and `state="STOPPED"` MUST be returned

#### Scenario: Missing prompt returns 400
- GIVEN a POST request with JSON body missing the `prompt` field
- WHEN the handler processes the request
- THEN an `ErrorResponse` with `status=400` and `error="invalid_params"` MUST be returned

#### Scenario: Empty body returns 400
- GIVEN a POST request with empty body
- WHEN the handler processes the request
- THEN an `ErrorResponse` with `status=400` and `error="invalid_params"` MUST be returned

### Requirement: Agent stop handler

The `POST /agent/stop` handler MUST look up an agent by query param `id` and call `stop()`.

#### Scenario: Existing agent returns 200
- GIVEN an agent was created via `/agent/run`
- WHEN a POST to `/agent/stop?id=<agentId>` is sent
- THEN HTTP 200 MUST be returned

#### Scenario: Unknown agent ID returns 404
- GIVEN no agent with the given ID exists
- WHEN a POST to `/agent/stop?id=nonexistent` is sent
- THEN an `ErrorResponse` with `status=404` and `error="not_found"` MUST be returned

#### Scenario: Missing id param returns 400
- GIVEN a POST to `/agent/stop` without query param `id`
- WHEN the handler processes the request
- THEN an `ErrorResponse` with `status=400` and `error="invalid_params"` MUST be returned

### Requirement: Agent state handler

The `GET /agent/state` handler MUST look up an agent by query param `id` and return an `AgentStateResponse`.

#### Scenario: Existing agent returns state
- GIVEN an agent was created via `/agent/run`
- WHEN a GET to `/agent/state?id=<agentId>` is sent
- THEN an `AgentStateResponse` with `agentId`, `state`, `createdAt`, and `updatedAt` MUST be returned with HTTP 200

#### Scenario: Unknown agent ID returns 404
- GIVEN no agent with the given ID exists
- WHEN a GET to `/agent/state?id=nonexistent` is sent
- THEN an `ErrorResponse` with `status=404` MUST be returned

#### Scenario: Missing id param returns 400
- GIVEN a GET to `/agent/state` without query param `id`
- WHEN the handler processes the request
- THEN an `ErrorResponse` with `status=400` MUST be returned

### Requirement: Tools list handler

The `GET /tools` handler MUST list registered tools as `ToolsListResponse`.

#### Scenario: Returns registered tools
- GIVEN the SDK has registered tools
- WHEN a GET to `/tools` is sent
- THEN a `ToolsListResponse` containing `ToolInfo` entries for each registered tool MUST be returned with HTTP 200

### Requirement: Health handler

The `GET /health` handler MUST return a `HealthResponse`.

#### Scenario: Healthy response
- GIVEN any state
- WHEN a GET to `/health` is sent
- THEN a `HealthResponse` with `status="ok"` and `version="0.1.0"` MUST be returned with HTTP 200

### Requirement: Agent tracking

The servlet MUST track agents created by `/agent/run` in an in-memory map keyed by agent ID for lookup by `/agent/stop` and `/agent/state`.

#### Scenario: Agent is tracked after run
- GIVEN an agent was created via `/agent/run`
- WHEN the agent ID is used in `/agent/state`
- THEN the agent MUST be found and its state returned

#### Scenario: Agent remains tracked after stop
- GIVEN an agent was created via `/agent/run` and then stopped via `/agent/stop`
- WHEN the agent ID is used in `/agent/state`
- THEN the agent MUST still be found with `state="STOPPED"`

### Requirement: Error response format

All error responses MUST use `HttpJsonCodec.encode(ErrorResponse)` with `Content-Type: application/json; charset=utf-8`.

#### Scenario: HttpApiException produces ErrorResponse
- GIVEN an `HttpApiException` with status 400, error "invalid_params", message "Bad"
- WHEN the servlet catches it
- THEN the response MUST have HTTP status 400 and a JSON body containing `"status":400`, `"error":"invalid_params"`, `"message":"Bad"`

### Requirement: SDK exception mapping

The servlet MUST map SDK exceptions to appropriate HTTP status codes.

#### Scenario: SdkLlmException maps to 502
- GIVEN an `SdkLlmException`
- WHEN `mapException` is called
- THEN it MUST return an `HttpApiException` with `httpStatus=502` and `errorCode="llm_error"`

#### Scenario: SdkPermissionException maps to 403
- GIVEN an `SdkPermissionException`
- WHEN `mapException` is called
- THEN it MUST return an `HttpApiException` with `httpStatus=403` and `errorCode="permission_denied"`

#### Scenario: SdkToolException maps to 422
- GIVEN an `SdkToolException`
- WHEN `mapException` is called
- THEN it MUST return an `HttpApiException` with `httpStatus=422` and `errorCode="tool_error"`

#### Scenario: Unhandled exception maps to 500
- GIVEN a generic `RuntimeException`
- WHEN `mapException` is called
- THEN it MUST return an `HttpApiException` with `httpStatus=500` and `errorCode="internal"`

---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/http/AgentStateResponse.java
    - src/main/java/org/specdriven/agent/http/AuthFilter.java
    - src/main/java/org/specdriven/agent/http/ErrorResponse.java
    - src/main/java/org/specdriven/agent/http/HealthResponse.java
    - src/main/java/org/specdriven/agent/http/HttpApiException.java
    - src/main/java/org/specdriven/agent/http/HttpApiServlet.java
    - src/main/java/org/specdriven/agent/http/HttpJsonCodec.java
    - src/main/java/org/specdriven/agent/http/RateLimitFilter.java
    - src/main/java/org/specdriven/agent/http/RunAgentRequest.java
    - src/main/java/org/specdriven/agent/http/RunAgentResponse.java
    - src/main/java/org/specdriven/agent/http/ToolInfo.java
    - src/main/java/org/specdriven/agent/http/ToolsListResponse.java
  tests:
    - src/test/java/org/specdriven/agent/http/AuthFilterTest.java
    - src/test/java/org/specdriven/agent/http/HttpApiServletTest.java
    - src/test/java/org/specdriven/agent/http/HttpModelsTest.java
    - src/test/java/org/specdriven/agent/http/RateLimitFilterTest.java
---

# http-api.md

## Requirements

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

### Requirement: RunAgentRequest type

The system MUST provide an immutable `RunAgentRequest` record in `org.specdriven.agent.http` representing an HTTP request to run an agent.

#### Scenario: Create with required fields
- GIVEN a prompt `"explain this code"`
- WHEN a `RunAgentRequest` is created with `prompt="explain this code"`
- THEN `prompt()` MUST return `"explain this code"`
- AND `systemPrompt()` MUST return null
- AND `maxTurns()` MUST return null
- AND `toolTimeoutSeconds()` MUST return null

#### Scenario: Create with all fields
- GIVEN prompt `"review"`, system prompt `"You are a reviewer"`, maxTurns `10`, toolTimeoutSeconds `30`
- WHEN a `RunAgentRequest` is created with all fields
- THEN all accessors MUST return the provided values

#### Scenario: Null prompt is rejected
- GIVEN a null prompt
- WHEN a `RunAgentRequest` is constructed
- THEN the compact constructor MUST throw `NullPointerException`

### Requirement: RunAgentResponse type

The system MUST provide an immutable `RunAgentResponse` record in `org.specdriven.agent.http` representing the result of an agent run.

#### Scenario: Success response
- GIVEN `agentId="abc-123"`, `output="explanation text"`, `state="STOPPED"`
- WHEN a `RunAgentResponse` is created
- THEN all accessors MUST return the provided values

#### Scenario: Running response with no output
- GIVEN `agentId="abc-123"`, `output=null`, `state="RUNNING"`
- WHEN a `RunAgentResponse` is created
- THEN `output()` MUST return null

### Requirement: AgentStateResponse type

The system MUST provide an immutable `AgentStateResponse` record in `org.specdriven.agent.http` representing an agent state query result.

#### Scenario: State response
- GIVEN `agentId="abc-123"`, `state="RUNNING"`, `createdAt=1000L`, `updatedAt=2000L`
- WHEN an `AgentStateResponse` is created
- THEN all accessors MUST return the provided values

### Requirement: ToolInfo type

The system MUST provide an immutable `ToolInfo` record in `org.specdriven.agent.http` representing a tool's metadata.

#### Scenario: Tool info
- GIVEN `name="bash"`, `description="Execute shell commands"`, `parameters=[Map.of("name","command","type","string")]`
- WHEN a `ToolInfo` is created
- THEN all accessors MUST return the provided values

#### Scenario: Empty parameters
- GIVEN `name="health"`, `description="Health check"`, `parameters=null`
- WHEN a `ToolInfo` is created
- THEN `parameters()` MUST return an empty list

### Requirement: ToolsListResponse type

The system MUST provide an immutable `ToolsListResponse` record in `org.specdriven.agent.http` representing the response from listing tools.

#### Scenario: List with tools
- GIVEN a list of `ToolInfo` records
- WHEN a `ToolsListResponse` is created
- THEN `tools()` MUST return the provided list

#### Scenario: Empty list
- GIVEN `tools=null`
- WHEN a `ToolsListResponse` is created
- THEN `tools()` MUST return an empty list

### Requirement: HealthResponse type

The system MUST provide an immutable `HealthResponse` record in `org.specdriven.agent.http` representing a health check result.

#### Scenario: Healthy response
- GIVEN `status="ok"`, `version="0.1.0"`
- WHEN a `HealthResponse` is created
- THEN `status()` MUST return `"ok"`
- AND `version()` MUST return `"0.1.0"`

### Requirement: ErrorResponse type

The system MUST provide an immutable `ErrorResponse` record in `org.specdriven.agent.http` representing a structured API error.

#### Scenario: Error with details
- GIVEN `status=400`, `error="invalid_params"`, `message="Missing prompt"`, `details=Map.of("field","prompt")`
- WHEN an `ErrorResponse` is created
- THEN all accessors MUST return the provided values

#### Scenario: Error without details
- GIVEN `status=500`, `error="internal"`, `message="Unexpected error"`, `details=null`
- WHEN an `ErrorResponse` is created
- THEN `details()` MUST return null

### Requirement: HttpApiException

The system MUST provide an `HttpApiException` in `org.specdriven.agent.http` extending `RuntimeException` for HTTP-layer errors.

#### Scenario: Exception carries HTTP status
- GIVEN an `HttpApiException` with status 400, error code `"invalid_params"`, and message `"Missing prompt"`
- THEN `httpStatus()` MUST return 400
- AND `errorCode()` MUST return `"invalid_params"`
- AND `getMessage()` MUST return `"Missing prompt"`

#### Scenario: Exception to ErrorResponse conversion
- GIVEN an `HttpApiException`
- WHEN `toErrorResponse()` is called
- THEN it MUST return an `ErrorResponse` with matching status, error, and message

### Requirement: HttpJsonCodec encoding

The system MUST provide a `HttpJsonCodec` class in `org.specdriven.agent.http` with static methods for encoding model types to JSON strings.

#### Scenario: Encode RunAgentResponse
- GIVEN a `RunAgentResponse` with `agentId="abc"`, `output="hi"`, `state="STOPPED"`
- WHEN `HttpJsonCodec.encode(response)` is called
- THEN the result MUST be a valid JSON string containing `"agentId":"abc"`, `"output":"hi"`, `"state":"STOPPED"`

#### Scenario: Encode HealthResponse
- GIVEN a `HealthResponse` with `status="ok"`, `version="0.1.0"`
- WHEN `HttpJsonCodec.encode(response)` is called
- THEN the result MUST contain `"status":"ok"` and `"version":"0.1.0"`

#### Scenario: Encode ErrorResponse
- GIVEN an `ErrorResponse` with `status=400`, `error="bad_request"`, `message="invalid"`
- WHEN `HttpJsonCodec.encode(error)` is called
- THEN the result MUST contain `"status":400`, `"error":"bad_request"`, `"message":"invalid"`

#### Scenario: Encode ToolsListResponse
- GIVEN a `ToolsListResponse` with two `ToolInfo` entries
- WHEN `HttpJsonCodec.encode(response)` is called
- THEN the result MUST contain a `"tools"` array with two elements

### Requirement: HttpJsonCodec decoding

The system MUST provide static methods on `HttpJsonCodec` for decoding JSON strings into request types.

#### Scenario: Decode RunAgentRequest
- GIVEN a JSON string `{"prompt":"explain this code"}`
- WHEN `HttpJsonCodec.decodeRequest(json, RunAgentRequest.class)` is called
- THEN it MUST return a `RunAgentRequest` with `prompt="explain this code"`

#### Scenario: Decode RunAgentRequest with all fields
- GIVEN a JSON string `{"prompt":"review","systemPrompt":"You are a reviewer","maxTurns":10,"toolTimeoutSeconds":30}`
- WHEN `HttpJsonCodec.decodeRequest(json, RunAgentRequest.class)` is called
- THEN all fields MUST match the JSON values

#### Scenario: Reject missing required fields
- GIVEN a JSON string `{"systemPrompt":"You are a reviewer"}`
- WHEN `HttpJsonCodec.decodeRequest(json, RunAgentRequest.class)` is called
- THEN it MUST throw `HttpApiException` with `httpStatus=400` and `errorCode="invalid_params"`

### Requirement: Authentication filter

The system MUST provide `AuthFilter` in `org.specdriven.agent.http` extending `jakarta.servlet.http.HttpFilter`, registered on `/api/v1/*`.

#### Scenario: Valid Bearer token passes auth
- GIVEN a request with header `Authorization: Bearer <valid-key>`
- WHEN `doFilter()` is called
- THEN the filter MUST call `chain.doFilter()` to continue processing

#### Scenario: Valid X-API-Key header passes auth
- GIVEN a request with header `X-API-Key: <valid-key>`
- WHEN `doFilter()` is called
- THEN the filter MUST call `chain.doFilter()` to continue processing

#### Scenario: Missing auth header returns 401
- GIVEN a request without `Authorization` or `X-API-Key` header
- WHEN `doFilter()` is called
- THEN an `ErrorResponse` with `status=401` and `error="unauthorized"` MUST be returned
- AND `chain.doFilter()` MUST NOT be called

#### Scenario: Invalid API key returns 401
- GIVEN a request with header `Authorization: Bearer invalid-key`
- WHEN `doFilter()` is called
- THEN an `ErrorResponse` with `status=401` and `error="unauthorized"` MUST be returned

#### Scenario: Health endpoint bypasses auth
- GIVEN a GET request to `/api/v1/health` without any auth header
- WHEN `doFilter()` is called
- THEN the filter MUST call `chain.doFilter()` without checking credentials

### Requirement: Rate limit filter

The system MUST provide `RateLimitFilter` in `org.specdriven.agent.http` extending `jakarta.servlet.http.HttpFilter`, registered on `/api/v1/*`.

#### Scenario: Requests under limit pass through
- GIVEN a client making requests within the configured rate limit
- WHEN `doFilter()` is called
- THEN the filter MUST call `chain.doFilter()` to continue processing

#### Scenario: Requests over limit return 429
- GIVEN a client has exceeded the configured request limit within the current window
- WHEN `doFilter()` is called
- THEN an `ErrorResponse` with `status=429` and `error="rate_limited"` MUST be returned
- AND a `Retry-After` header MUST be set with the remaining seconds in the current window

#### Scenario: Window reset allows new requests
- GIVEN a client was rate-limited in a previous window
- AND the window has expired
- WHEN the client makes a new request
- THEN the filter MUST call `chain.doFilter()` to continue processing

### Requirement: Middleware error response format

All middleware error responses MUST use `HttpJsonCodec.encode(ErrorResponse)` with `Content-Type: application/json; charset=utf-8`, matching the existing servlet error format.

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
    - src/main/java/org/specdriven/agent/http/RemoteHttpTool.java
    - src/main/java/org/specdriven/agent/http/RemoteToolInvocationRequest.java
    - src/main/java/org/specdriven/agent/http/RemoteToolInvocationResponse.java
    - src/main/java/org/specdriven/agent/http/RemoteToolRegistrationRequest.java
    - src/main/java/org/specdriven/agent/http/RunAgentRequest.java
    - src/main/java/org/specdriven/agent/http/RunAgentResponse.java
    - src/main/java/org/specdriven/agent/http/ToolInfo.java
    - src/main/java/org/specdriven/agent/http/PlatformHealthResponse.java
    - src/main/java/org/specdriven/agent/http/ToolsListResponse.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
  tests:
    - src/test/java/org/specdriven/agent/http/AuthFilterTest.java
    - src/test/java/org/specdriven/agent/http/HttpApiServletTest.java
    - src/test/java/org/specdriven/agent/http/HttpModelsTest.java
    - src/test/java/org/specdriven/agent/http/RateLimitFilterTest.java
    - src/test/java/org/specdriven/sdk/SdkAgentTest.java
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

#### Scenario: GET /events routes to event polling handler
- GIVEN a GET request with `pathInfo="/events"`
- WHEN `service()` is called
- THEN an event polling response MUST be returned with HTTP 200

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

### Requirement: HTTP event polling endpoint

The HTTP REST API MUST expose an authenticated event polling endpoint at `GET /api/v1/events`.

#### Scenario: Poll events returns retained events
- GIVEN the HTTP API has observed backend events after servlet initialization
- WHEN an authenticated caller sends `GET /api/v1/events`
- THEN the response MUST contain the retained events in ascending sequence order
- AND each returned event MUST include `sequence`, `type`, `timestamp`, `source`, and `metadata`

#### Scenario: Poll events uses after cursor
- GIVEN the HTTP API has retained events with sequences greater than and less than a cursor value
- WHEN an authenticated caller sends `GET /api/v1/events?after=<cursor>`
- THEN the response MUST include only events whose sequence is greater than `<cursor>`

#### Scenario: Poll events returns next cursor
- GIVEN an authenticated caller polls events
- WHEN the endpoint returns successfully
- THEN the response MUST include `nextCursor`
- AND `nextCursor` MUST be the greatest returned event sequence when events are returned
- AND `nextCursor` MUST remain usable as the next `after` cursor when no events are returned

#### Scenario: Poll events filters by type
- GIVEN the HTTP API has retained events with different event types
- WHEN an authenticated caller sends `GET /api/v1/events?type=AGENT_STATE_CHANGED`
- THEN the response MUST include only retained events with that type

#### Scenario: Poll events applies limit
- GIVEN the HTTP API has retained more events than the requested limit
- WHEN an authenticated caller sends `GET /api/v1/events?limit=2`
- THEN the response MUST include no more than two events
- AND the returned events MUST be the earliest matching events after the cursor

#### Scenario: Invalid event query returns 400
- GIVEN a caller supplies an invalid `after`, `limit`, or `type` query value
- WHEN the event polling endpoint processes the request
- THEN an `ErrorResponse` with `status=400` and `error="invalid_params"` MUST be returned

### Requirement: HTTP event polling compatibility

The event polling endpoint MUST preserve existing HTTP REST API behavior.

#### Scenario: Existing routes remain compatible
- GIVEN callers use `/health`, `/tools`, `/agent/run`, `/agent/stop`, `/agent/state`, `/callbacks/{channel}`, or `/delivery/status/{questionId}`
- WHEN the event polling endpoint is available
- THEN those existing routes MUST keep their existing observable behavior

#### Scenario: Health remains unauthenticated
- GIVEN `/api/v1/events` requires authentication through the normal filter chain
- WHEN a caller sends `GET /api/v1/health`
- THEN health endpoint authentication behavior MUST remain unchanged

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

### Requirement: Platform health route

The HTTP API MUST provide a `GET /platform/health` route that returns platform subsystem health when a `LealonePlatform` is assembled into the SDK.

#### Scenario: Platform health returns aggregated status
- GIVEN an `HttpApiServlet` backed by a `SpecDriven` instance that has an assembled `LealonePlatform`
- WHEN a GET request with `pathInfo="/platform/health"` is received
- THEN a `PlatformHealthResponse` MUST be returned with HTTP 200
- AND the response MUST include `overallStatus`, a `subsystems` array (each with `name`, `status`, and optional `message`), and `probedAt`

#### Scenario: Platform health returns 404 when no platform is assembled
- GIVEN an `HttpApiServlet` backed by a `SpecDriven` instance built without `buildPlatform()`
- WHEN a GET request with `pathInfo="/platform/health"` is received
- THEN an `ErrorResponse` with `status=404` and `error="not_found"` MUST be returned

### Requirement: PlatformHealthResponse type

The system MUST provide an immutable `PlatformHealthResponse` record in `org.specdriven.agent.http` for JSON serialization of platform health results.

#### Scenario: Response carries subsystem details
- GIVEN a `PlatformHealth` with two subsystems
- WHEN a `PlatformHealthResponse` is constructed from it
- THEN `overallStatus()` MUST match the `PlatformHealth` overall status name
- AND `subsystems()` MUST contain one entry per `SubsystemHealth` with matching name, status, and message

### Requirement: Route dispatching — platform health

The servlet MUST additionally dispatch `GET /platform/health` to the platform health handler.

### Requirement: HttpJsonCodec encoding — PlatformHealthResponse

`HttpJsonCodec` MUST additionally support `encode(PlatformHealthResponse)`.

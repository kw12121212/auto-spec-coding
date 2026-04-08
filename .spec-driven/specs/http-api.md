# http-api.md

## ADDED Requirements

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

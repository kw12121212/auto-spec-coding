# Tasks: http-models

## Implementation

- [x] Create `RunAgentRequest` record in `org.specdriven.agent.http` with fields `prompt` (String, required), `systemPrompt` (String, nullable), `maxTurns` (Integer, nullable), `toolTimeoutSeconds` (Integer, nullable)
- [x] Create `RunAgentResponse` record with fields `agentId` (String), `output` (String), `state` (String)
- [x] Create `AgentStateResponse` record with fields `agentId` (String), `state` (String), `createdAt` (long), `updatedAt` (long)
- [x] Create `ToolInfo` record with fields `name` (String), `description` (String), `parameters` (List<Map<String, Object>>)
- [x] Create `ToolsListResponse` record with field `tools` (List<ToolInfo>)
- [x] Create `HealthResponse` record with fields `status` (String), `version` (String)
- [x] Create `ErrorResponse` record with fields `status` (int), `error` (String), `message` (String), `details` (Map<String, Object>, nullable)
- [x] Create `HttpApiException` in `org.specdriven.agent.http` with HTTP status code, error code string, and message; extending RuntimeException
- [x] Create `HttpJsonCodec` in `org.specdriven.agent.http` with static `encode` and `decode` methods for all model types using project JsonWriter/JsonReader

## Testing

- [x] Lint: run `mvn compile` — must pass with zero errors
- [x] Unit tests: run `mvn test` — must pass with zero failures
- [x] Unit test `RunAgentRequest` construction and field access, including null `prompt` rejection
- [x] Unit test each response record construction and field access
- [x] Unit test `HttpJsonCodec.encode` produces valid JSON for each response type
- [x] Unit test `HttpJsonCodec.decode` parses valid JSON into correct request type
- [x] Unit test `HttpJsonCodec.decode` rejects missing required fields with `HttpApiException`
- [x] Unit test `HttpApiException` carries status code, error code, and message

## Verification

- [x] Verify all models are immutable records with no setter methods
- [x] Verify package is `org.specdriven.agent.http`
- [x] Verify no external JSON library dependency introduced
- [x] Verify implementation matches proposal scope

# Tasks: http-routes

## Implementation

- [x] Add `lealone-http` dependency to `pom.xml`
- [x] Implement `HttpApiServlet` in `org.specdriven.agent.http` — extends `HttpServlet`, registers on `/api/v1/*`
- [x] Implement path and method dispatching in `HttpApiServlet.service()` — parse `getPathInfo()` segments and `getMethod()` to route to handlers
- [x] Implement `POST /agent/run` handler — decode `RunAgentRequest` via `HttpJsonCodec`, create `SdkAgent`, call `run(prompt)`, encode `RunAgentResponse`
- [x] Implement `POST /agent/stop` handler — look up agent by query param `id`, call `stop()`
- [x] Implement `GET /agent/state` handler — look up agent by query param `id`, encode `AgentStateResponse`
- [x] Implement `GET /tools` handler — list registered tools, encode `ToolsListResponse`
- [x] Implement `GET /health` handler — encode `HealthResponse`
- [x] Implement agent tracking — `ConcurrentHashMap<String, SdkAgent>` to store agents created by `agent/run` for `stop`/`state` lookup
- [x] Implement error handling — catch `HttpApiException` and SDK exceptions, map to `ErrorResponse` with correct HTTP status
- [x] Write delta spec `changes/http-routes/specs/http-routes.md` describing route handler observable behavior

## Testing

- [x] Run lint/validation: `mvn compile` — verify no compilation errors
- [x] Implement unit tests for `HttpApiServlet` route dispatching — verify correct handler is selected for each (method, path) combination
- [x] Implement unit tests for `POST /agent/run` handler — valid request, missing prompt, SDK exception
- [x] Implement unit tests for `POST /agent/stop` handler — existing agent, unknown agent ID
- [x] Implement unit tests for `GET /agent/state` handler — existing agent, unknown agent ID
- [x] Implement unit tests for `GET /tools` handler — returns registered tools
- [x] Implement unit tests for `GET /health` handler — returns ok status
- [x] Implement unit tests for error handling — invalid routes, wrong methods, unhandled exceptions
- [x] Run `mvn test` to verify all tests pass

## Verification

- [x] Verify all 5 endpoints match the route table in design.md
- [x] Verify error responses use `ErrorResponse` with correct HTTP status codes
- [x] Verify `HttpJsonCodec` is used for all encode/decode operations
- [x] Verify agent tracking works across run/stop/state operations

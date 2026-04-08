# http-routes — Design

## Approach

Register a single `HttpApiServlet` (extends `jakarta.servlet.http.HttpServlet`) on the URL pattern `/api/v1/*` via Lealone's `TomcatRouter.addServlet()`. The servlet's `service()` method dispatches requests by parsing `request.getPathInfo()` for the path segments and `request.getMethod()` for the HTTP verb.

### Route table

| Method | Path | SDK Operation | Request Body | Response |
|--------|------|---------------|-------------|----------|
| POST | /agent/run | `sdk.createAgent().run(prompt)` | RunAgentRequest | RunAgentResponse |
| POST | /agent/stop | `agent.stop()` | — | 200 empty |
| GET | /agent/state | `agent.getState()` | — | AgentStateResponse |
| GET | /tools | `sdk.getRegisteredTools()` | — | ToolsListResponse |
| GET | /health | (no SDK call) | — | HealthResponse |

### Dispatch logic

```
pathInfo = request.getPathInfo()   // e.g. "/agent/run"
method   = request.getMethod()     // e.g. "POST"

Split pathInfo by "/" → ["", "agent", "run"]
Route to handler by (method, segments[1], segments[2])
```

### SDK lifecycle

- The `SpecDriven` SDK instance is created once at servlet `init()` time (or injected via servlet constructor).
- `agent/run`: creates a new `SdkAgent` per request via `sdk.createAgent()`, calls `agent.run(prompt)`, returns output.
- `agent/stop` and `agent/state`: need a mechanism to track the current agent. Use a `ConcurrentHashMap<String, SdkAgent>` keyed by agent session ID. The `agent/run` handler stores the agent; `stop`/`state` look it up by ID passed as query param `?id=...`.

### Error handling

- Invalid route or method → `HttpApiException(404, "not_found", ...)` → `ErrorResponse`
- Invalid request body → `HttpApiException(400, "invalid_params", ...)` → `ErrorResponse`
- SDK exceptions (`SdkLlmException`, `SdkPermissionException`, etc.) caught and mapped to `HttpApiException` with appropriate HTTP status
- Unhandled exceptions → `HttpApiException(500, "internal", ...)` → `ErrorResponse`

### Response serialization

All responses use `HttpJsonCodec.encode()` to produce JSON. Content-Type is `application/json; charset=utf-8`.

## Key Decisions

1. **Single servlet with manual routing** — Lealone's servlet model doesn't support path parameters or method-based dispatch natively. A single servlet on `/api/v1/*` with manual path parsing is the simplest approach, matching how Lealone's own `TomcatServiceServlet` works.

2. **Agent tracking via in-memory map** — `agent/stop` and `agent/state` need to reference a running agent. Store agents in a `ConcurrentHashMap<String, SdkAgent>` within the servlet. This is acceptable for single-process deployment; multi-instance scenarios would need external state (out of scope).

3. **No middleware in this change** — Authentication and rate-limiting filters will be added via Lealone's filter mechanism in the `http-middleware` change. Routes assume unauthenticated access for now.

4. **Path-based versioning** — `/api/v1/` prefix allows future API versions without breaking existing clients.

## Alternatives Considered

- **One servlet per route** — Would require 5 servlet registrations. More verbose, no clear benefit since all routes share the same SDK instance and error handling.
- **JAX-RS (Jersey)** — Would add a significant dependency and complexity. Overkill for 5 endpoints.
- **Service-execution model (Lealone CREATE SERVICE)** — Lealone's service invocation pattern maps URLs to SQL services. Not suitable for imperative SDK operations with complex request/response types.

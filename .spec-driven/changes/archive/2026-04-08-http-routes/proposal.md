# http-routes

## What

Implement REST API route definitions and request handlers on Lealone's embedded Tomcat HTTP server, mapping HTTP requests under `/api/v1/*` to SDK operations (agent run/stop/state, tools list, health check).

## Why

M14 (HTTP REST API) requires a network-reachable interface for agent services. The `http-models` change is complete (request/response types, JSON codec, error types). The JSON-RPC interface (M13) provides a proven pattern for mapping SDK operations to a transport layer. This change provides the routing layer that connects HTTP requests to the existing SDK facade.

## Scope

- Register a servlet on `/api/v1/*` with Lealone's TomcatRouter
- Implement route dispatching by parsing `request.getPathInfo()` and `request.getMethod()`
- Define handlers for: `POST /agent/run`, `POST /agent/stop`, `GET /agent/state`, `GET /tools`, `GET /health`
- Use `HttpJsonCodec` for request decoding and response encoding
- Use `HttpApiException` for error handling, mapping to `ErrorResponse`
- Add `lealone-http` Maven dependency to pom.xml

## Unchanged Behavior

- Existing `http-models` types (RunAgentRequest, RunAgentResponse, etc.) remain unchanged
- `HttpJsonCodec` encode/decode API remains unchanged
- SDK public API (SpecDriven, SdkAgent, SdkBuilder) remains unchanged
- JSON-RPC interface remains unchanged
- Authentication and rate-limiting middleware deferred to `http-middleware` change

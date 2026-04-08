# http-models

## What

Define immutable Java record types for the HTTP REST API request and response models, including agent operations (run, stop, state), tool operations (list), health checks, and structured error responses.

## Why

M13 (JSON-RPC Interface) is complete. M14 (HTTP REST API) continues the interface layer progression. The HTTP models are the foundation that `http-routes`, `http-middleware`, and `http-e2e-tests` all depend on — defining them first ensures consistent JSON serialization across all HTTP handlers. M20 (Go Client SDK) and M21 (TypeScript Client SDK) also need stable HTTP models to generate their client code.

## Scope

- Request models: `RunAgentRequest`
- Response models: `RunAgentResponse`, `AgentStateResponse`, `ToolsListResponse`, `HealthResponse`, `ErrorResponse`
- JSON codec: `HttpJsonCodec` for encoding/decoding models to/from JSON
- API error exception: `HttpApiException` for HTTP-layer error mapping
- Package: `org.specdriven.agent.http`

## Unchanged Behavior

- All existing JSON-RPC, SDK, agent, and tool interfaces remain unchanged
- No new HTTP server or routing logic — this change defines data types only

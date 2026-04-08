# Design: http-models

## Approach

Define all HTTP API models as Java records in `org.specdriven.agent.http`, mirroring the pattern established by `JsonRpcRequest`/`JsonRpcResponse` in `org.specdriven.agent.jsonrpc`. Use the project's existing `JsonWriter`/`JsonReader` utilities for JSON serialization, consistent with the `JsonRpcCodec` pattern. Provide a `HttpJsonCodec` utility for encoding/decoding, and an `HttpApiException` for structured error responses.

The API surface covers five REST resources mapped from the existing JSON-RPC dispatch operations:

| JSON-RPC method | REST endpoint | HTTP method |
|---|---|---|
| `agent/run` | `/api/v1/agents` | POST |
| `agent/stop` | `/api/v1/agents/{id}/stop` | POST |
| `agent/state` | `/api/v1/agents/{id}` | GET |
| `tools/list` | `/api/v1/tools` | GET |
| — | `/api/v1/health` | GET |

## Key Decisions

- **Records for immutability**: All models are Java records, consistent with `JsonRpcRequest`, `JsonRpcResponse`, `ToolCall`, etc.
- **Flat response types**: Each endpoint gets its own response record rather than a generic envelope — this provides type safety and clear field names per endpoint.
- **ErrorResponse as a standard shape**: All error responses share `status`, `error` (code string), `message`, and optional `details` — consistent with common REST API conventions.
- **API versioning in path**: `/api/v1/` prefix for future versioning flexibility.
- **Project JsonWriter/JsonReader for JSON**: Uses the same lightweight JSON utilities as `JsonRpcCodec`, no new dependencies.

## Alternatives Considered

- **Generic envelope response** (e.g., `ApiResponse<T>`): Rejected — Java records with generics add complexity without benefit for a fixed set of endpoints.
- **Jackson/Gson for JSON**: Rejected — the project already has lightweight JsonWriter/JsonReader utilities; adding another library violates the minimal-dependency principle.
- **Shared models with JSON-RPC types**: Rejected — the two interfaces have different shapes (REST has HTTP status codes, path params, no request IDs) and should evolve independently.

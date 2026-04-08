# Design: jsonrpc-protocol

## Approach

Create a `org.specdriven.agent.jsonrpc` package with four immutable value types and a static codec class:

1. **`JsonRpcRequest`** — represents a JSON-RPC 2.0 request object (`jsonrpc`, `id`, `method`, `params`). `id` is typed as `Object` to support String/Long/null per spec. `params` is `Map<String, Object>` (by-position params are not needed for this use case but the codec will handle both by-name and by-position forms).
2. **`JsonRpcNotification`** — same structure as request but `id` is absent. A distinct type to prevent accidental response generation.
3. **`JsonRpcResponse`** — represents a JSON-RPC 2.0 response (`jsonrpc`, `id`, `result`, `error`). Exactly one of `result` or `error` is present; constructor enforces this invariant.
4. **`JsonRpcError`** — represents the error object (`code`, `message`, `data`). Predefined constants for standard error codes.
5. **`JsonRpcCodec`** — static methods `encodeResponse`, `encodeNotification`, `decodeRequest` that bridge between the value types and raw JSON strings using `JsonReader`/`JsonWriter`.

All types use records where appropriate (`JsonRpcError`, `JsonRpcResponse`) or immutable classes with final fields. No inheritance hierarchy — each type is standalone.

## Key Decisions

- **Use existing `JsonReader`/`JsonWriter`** — avoids adding Jackson/Gson dependencies. These utilities already handle all needed JSON primitives, nested maps, and arrays.
- **`id` as `Object`** — JSON-RPC 2.0 allows String, Number, or null for `id`. Using `Object` matches the parsed output of `JsonReader` naturally.
- **Separate `JsonRpcNotification` from `JsonRpcRequest`** — they have identical structure except for the absence of `id`. A distinct type makes the handler layer cleaner and prevents bugs where a notification accidentally gets a response.
- **No batch support** — batch requests are out of scope for M13 per the milestone notes, but the codec will not reject batch arrays at parse time (they will cause a clear error message so the caller can handle them later).
- **Custom error codes** — standard codes as constants in `JsonRpcError`, but the constructor accepts any integer for application-specific errors (range `-32768` to `-32000` is reserved by spec).

## Alternatives Considered

- **Lealone `JsonObject`** — used in stores (LealoneTaskStore, etc.) but requires Lealone DB dependency. Protocol types should be dependency-free for reuse in transport layer.
- **Jackson/Gson** — rejected to maintain the project's zero-external-JSON-library policy. The existing `JsonReader`/`JsonWriter` are sufficient.
- **Single `JsonRpcMessage` with discriminators** — rejected because request/notification/response have different mandatory fields and different lifecycle semantics (notifications don't get responses). Separate types enforce correctness at compile time.
- **By-position params as `List<Object>`** — JSON-RPC 2.0 supports both by-name (`Map`) and by-position (`List`) params. For this SDK's use case, by-name is primary. The codec will parse both forms but `JsonRpcRequest.params()` will expose them as `Object` (either `Map` or `List`).

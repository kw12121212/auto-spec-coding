# Tasks: jsonrpc-protocol

## Implementation

- [x] Create `JsonRpcError` immutable type with standard error code constants (`PARSE_ERROR`, `INVALID_REQUEST`, `METHOD_NOT_FOUND`, `INVALID_PARAMS`, `INTERNAL_ERROR`) and constructor for custom codes
- [x] Create `JsonRpcRequest` immutable type with `jsonrpc`, `id` (Object), `method` (String), `params` (Object — Map or List) fields; validate `id` and `method` are non-null
- [x] Create `JsonRpcNotification` immutable type with `jsonrpc`, `method`, `params` fields; no `id` field
- [x] Create `JsonRpcResponse` immutable type with `jsonrpc`, `id`, `result`, `error` fields; validate exactly one of `result`/`error` is non-null (except `result=null` is valid for success with null result)
- [x] Create `JsonRpcProtocolException` extending `RuntimeException` with `errorCode` field and descriptive message
- [x] Implement `JsonRpcCodec.encode(Response)` using `JsonWriter` — encode success/error response with field order `jsonrpc`, `id`, `result`/`error`
- [x] Implement `JsonRpcCodec.encode(Notification)` using `JsonWriter` — encode without `id` field
- [x] Implement `JsonRpcCodec.decodeRequest(String)` using `JsonReader` — parse JSON, detect request vs notification by presence of `id`, validate `jsonrpc` version and `method`, handle by-name and by-position params
- [x] Handle edge cases: missing `jsonrpc` field, wrong version, missing `method`, malformed JSON, missing `params` (default to empty)

## Testing

- [x] Lint: run `mvn compile` to verify compilation with no errors
- [x] Unit test: run `mvn test -Dtest="JsonRpc*Test"` to verify all JSON-RPC protocol tests pass

## Verification

- [x] Verify all spec scenarios in `jsonrpc-protocol.md` are covered by tests
- [x] Verify `JsonReader`/`JsonWriter` APIs are used without modification
- [x] Verify no external dependencies added to pom.xml

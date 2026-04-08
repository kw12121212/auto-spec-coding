# Tasks: jsonrpc-protocol

## Implementation

- [ ] Create `JsonRpcError` immutable type with standard error code constants (`PARSE_ERROR`, `INVALID_REQUEST`, `METHOD_NOT_FOUND`, `INVALID_PARAMS`, `INTERNAL_ERROR`) and constructor for custom codes
- [ ] Create `JsonRpcRequest` immutable type with `jsonrpc`, `id` (Object), `method` (String), `params` (Object — Map or List) fields; validate `id` and `method` are non-null
- [ ] Create `JsonRpcNotification` immutable type with `jsonrpc`, `method`, `params` fields; no `id` field
- [ ] Create `JsonRpcResponse` immutable type with `jsonrpc`, `id`, `result`, `error` fields; validate exactly one of `result`/`error` is non-null (except `result=null` is valid for success with null result)
- [ ] Create `JsonRpcProtocolException` extending `RuntimeException` with `errorCode` field and descriptive message
- [ ] Implement `JsonRpcCodec.encode(Response)` using `JsonWriter` — encode success/error response with field order `jsonrpc`, `id`, `result`/`error`
- [ ] Implement `JsonRpcCodec.encode(Notification)` using `JsonWriter` — encode without `id` field
- [ ] Implement `JsonRpcCodec.decodeRequest(String)` using `JsonReader` — parse JSON, detect request vs notification by presence of `id`, validate `jsonrpc` version and `method`, handle by-name and by-position params
- [ ] Handle edge cases: missing `jsonrpc` field, wrong version, missing `method`, malformed JSON, missing `params` (default to empty)

## Testing

- [ ] Lint: run `mvn compile` to verify compilation with no errors
- [ ] Unit test: run `mvn test -Dtest="JsonRpc*Test"` to verify all JSON-RPC protocol tests pass

## Verification

- [ ] Verify all spec scenarios in `jsonrpc-protocol.md` are covered by tests
- [ ] Verify `JsonReader`/`JsonWriter` APIs are used without modification
- [ ] Verify no external dependencies added to pom.xml

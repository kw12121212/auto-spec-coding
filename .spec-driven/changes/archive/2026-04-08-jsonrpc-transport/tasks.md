# Tasks: jsonrpc-transport

## Implementation

- [x] Create `JsonRpcMessageHandler` functional interface in `org.specdriven.agent.jsonrpc` with `onRequest(JsonRpcRequest)`, `onNotification(JsonRpcNotification)`, and `onError(Throwable)` methods
- [x] Create `JsonRpcTransport` interface in `org.specdriven.agent.jsonrpc` extending `AutoCloseable` with `start(JsonRpcMessageHandler)`, `stop()`, `send(JsonRpcResponse)`, `send(JsonRpcNotification)` methods
- [x] Create `StdioTransport` class in `org.specdriven.agent.jsonrpc` implementing `JsonRpcTransport` with `InputStream`/`OutputStream` constructor and Content-Length framing
- [x] Implement frame reading: parse `Content-Length` header from input stream, read declared byte count, decode via `JsonRpcCodec.decodeRequest()`, dispatch to handler
- [x] Implement frame writing: encode via `JsonRpcCodec.encode()`, write `Content-Length` header + body to output stream (synchronized)
- [x] Implement background daemon reader thread in `start()`, with interrupt-based `stop()` and graceful join
- [x] Add `maxMessageSize` constructor parameter (default 10 MB) with validation on read
- [x] Handle transport-level errors (malformed header, oversized message, stream closed) by calling `handler.onError()` and continuing the read loop where recoverable

## Testing

- [x] Validation: run `mvn compile` to ensure all new types compile without errors
- [x] Run `mvn test -pl . -Dtest=JsonRpcTransportTest` to verify transport unit tests
- [x] Write `JsonRpcTransportTest` (JUnit 5): test frame round-trip using `ByteArrayInputStream`/`ByteArrayOutputStream`
- [x] Test: send a `JsonRpcResponse`, read the framed bytes back, verify Content-Length header and body match
- [x] Test: send a `JsonRpcNotification`, read framed bytes back, verify no id field in output
- [x] Test: write framed request bytes to input stream, verify `onRequest` callback receives decoded `JsonRpcRequest`
- [x] Test: write framed notification bytes (no id) to input stream, verify `onNotification` callback fires
- [x] Test: oversized Content-Length triggers `onError` with appropriate exception
- [x] Test: `stop()` terminates the reader thread within a reasonable timeout
- [x] Test: malformed header (missing Content-Length) triggers `onError`

## Verification

- [x] Verify all transport types are in `org.specdriven.agent.jsonrpc` package
- [x] Verify existing protocol types (`JsonRpcRequest`, `JsonRpcResponse`, etc.) are not modified
- [x] Verify `mvn test` passes with no regressions
- [x] Verify delta spec `jsonrpc-transport.md` accurately reflects implemented behavior

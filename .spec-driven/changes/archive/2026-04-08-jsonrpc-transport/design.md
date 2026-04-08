# Design: jsonrpc-transport

## Approach

Implement a framed transport over stdin/stdout using the Content-Length header convention:

1. **Framing protocol**: Each message is preceded by a `Content-Length: <n>\r\n\r\n` header, followed by exactly `<n>` bytes of UTF-8 JSON. This is the same framing used by LSP and spec-coding-sdk.

2. **`JsonRpcTransport` interface**: Defines `send(JsonRpcResponse)` and `send(JsonRpcNotification)` for outbound messages, and a `start(MessageHandler)` / `stop()` lifecycle for inbound processing. Extends `AutoCloseable`.

3. **`StdioTransport` implementation**: Wraps an `InputStream` and `OutputStream`. On `start()`, spawns a daemon reader thread that continuously reads framed messages, decodes them via `JsonRpcCodec.decodeRequest()`, and dispatches to the registered `JsonRpcMessageHandler`. On `send()`, encodes via `JsonRpcCodec.encode()` and writes the frame header + body to the output stream (synchronized for thread safety).

4. **`JsonRpcMessageHandler` functional interface**: A callback receiving decoded `JsonRpcRequest` or `JsonRpcNotification` objects from the transport.

5. **Error handling**: Malformed frames or codec errors on the reader thread are reported to the handler via an `onError(Throwable)` method rather than crashing the reader loop. The transport continues reading after recoverable errors.

## Key Decisions

- **Content-Length framing over newline-delimited JSON**: Content-Length is binary-safe, avoids ambiguity with embedded newlines in JSON, and is the standard used by LSP and spec-coding-sdk. Newline-delimited JSON is simpler but cannot handle messages containing literal newlines in strings reliably.

- **Daemon reader thread**: The reader runs on a daemon thread so it does not prevent JVM shutdown. The transport's `stop()` method interrupts the reader and waits for it to finish.

- **Synchronized output writes**: Multiple threads may call `send()` concurrently. Synchronizing on the output stream prevents interleaved frames.

- **Max message size limit**: A configurable `maxMessageSize` (default 10 MB) prevents out-of-memory attacks from malformed Content-Length headers. Transport throws an exception if the declared content length exceeds the limit.

- **Separate `start()`/`stop()` from constructor**: Allows the caller to register the handler before reading begins, avoiding race conditions where messages arrive before the handler is set.

## Alternatives Considered

- **Newline-delimited JSON (NDJSON)**: Simpler framing but cannot handle JSON strings with embedded newlines. Would diverge from spec-coding-sdk's protocol.
- **Blocking queue-based design**: Could use a `BlockingQueue` between reader and consumer. Rejected — the callback-based design is simpler and gives the consumer full control over threading.
- **NIO/Selector-based transport**: Would support higher throughput but adds complexity. stdin/stdout is inherently single-stream, so blocking I/O is appropriate.

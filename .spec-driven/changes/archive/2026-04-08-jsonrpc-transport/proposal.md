# jsonrpc-transport

## What

Implement a stdin/stdout transport layer for JSON-RPC 2.0 messages, using Content-Length header framing to delimit messages on the byte stream.

## Why

The JSON-RPC protocol types and codec (`jsonrpc-protocol`) are complete. The transport layer is the next dependency — it provides the framed read/write capability over stdin/stdout that `jsonrpc-handlers` and `jsonrpc-e2e-tests` will build on. Without it, there is no way to exchange JSON-RPC messages with the agent process.

This is the standard framing approach used by LSP and other JSON-RPC-over-stdio protocols, ensuring compatibility with spec-coding-sdk's stdin protocol.

## Scope

In scope:
- A `JsonRpcTransport` interface for sending and receiving JSON-RPC messages over a bidirectional byte stream
- A `StdioTransport` implementation using `InputStream`/`OutputStream` with Content-Length header framing
- A `JsonRpcMessageHandler` callback interface for incoming messages
- Transport lifecycle: start, stop, close with graceful shutdown
- Thread-safe message reading (background reader thread) and writing (synchronized output)
- Buffer size limits to prevent unbounded memory allocation

Out of scope:
- Request routing and handler dispatch (belongs to `jsonrpc-handlers`)
- Batch request support (deferred per M13 milestone)
- HTTP transport (M14)
- Backpressure/concurrency control beyond basic thread safety (future iteration)

## Unchanged Behavior

- Existing `JsonRpcRequest`, `JsonRpcResponse`, `JsonRpcNotification`, `JsonRpcError`, `JsonRpcCodec`, and `JsonRpcProtocolException` types must not be modified
- The `SpecDriven` SDK facade and `SdkAgent` must not change

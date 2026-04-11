---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcCodec.java
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcMessageHandler.java
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcProtocolException.java
    - src/main/java/org/specdriven/agent/jsonrpc/JsonRpcTransport.java
    - src/main/java/org/specdriven/agent/jsonrpc/StdioTransport.java
  tests:
    - src/test/java/org/specdriven/agent/jsonrpc/JsonRpcTransportTest.java
---

# jsonrpc-transport.md

## ADDED Requirements

### Requirement: JsonRpcMessageHandler callback interface

The system MUST provide a `JsonRpcMessageHandler` interface in `org.specdriven.agent.jsonrpc` for receiving decoded inbound messages from the transport.

#### Scenario: Receive a request
- GIVEN a running transport with a registered handler
- WHEN a framed `JsonRpcRequest` arrives on the input stream
- THEN the handler's `onRequest(JsonRpcRequest)` method MUST be called with the decoded request

#### Scenario: Receive a notification
- GIVEN a running transport with a registered handler
- WHEN a framed `JsonRpcNotification` arrives on the input stream
- THEN the handler's `onNotification(JsonRpcNotification)` method MUST be called with the decoded notification

#### Scenario: Receive a transport error
- GIVEN a running transport with a registered handler
- WHEN a frame parsing error or decode error occurs
- THEN the handler's `onError(Throwable)` method MUST be called with the exception

### Requirement: JsonRpcTransport interface

The system MUST provide a `JsonRpcTransport` interface in `org.specdriven.agent.jsonrpc` extending `AutoCloseable` for sending and receiving framed JSON-RPC messages.

#### Scenario: Start transport
- GIVEN a `JsonRpcTransport` instance
- WHEN `start(handler)` is called
- THEN the transport MUST begin reading framed messages from its input and dispatching them to the handler

#### Scenario: Stop transport
- GIVEN a running `JsonRpcTransport`
- WHEN `stop()` is called
- THEN the transport MUST stop reading and the background reader MUST terminate

#### Scenario: Send a response
- GIVEN a `JsonRpcTransport` instance
- WHEN `send(response)` is called with a `JsonRpcResponse`
- THEN the encoded JSON frame MUST be written to the output stream

#### Scenario: Send a notification
- GIVEN a `JsonRpcTransport` instance
- WHEN `send(notification)` is called with a `JsonRpcNotification`
- THEN the encoded JSON frame MUST be written to the output stream

### Requirement: StdioTransport Content-Length framing

The system MUST provide a `StdioTransport` class in `org.specdriven.agent.jsonrpc` implementing `JsonRpcTransport` over `InputStream`/`OutputStream` with Content-Length header framing.

#### Scenario: Frame format on write
- GIVEN a `StdioTransport` wrapping a `ByteArrayOutputStream`
- WHEN `send(response)` is called with a response whose JSON body is 42 bytes
- THEN the output MUST start with `Content-Length: 42\r\n\r\n` followed by exactly 42 bytes of UTF-8 JSON

#### Scenario: Frame format on read
- GIVEN a `StdioTransport` wrapping a `ByteArrayInputStream` containing `Content-Length: 28\r\n\r\n{"jsonrpc":"2.0","id":1}`
- WHEN `start(handler)` is called
- THEN the handler MUST receive a decoded `JsonRpcRequest` with `id=1`

#### Scenario: Multiple frames on read
- GIVEN a `StdioTransport` wrapping input containing two consecutive valid frames
- WHEN `start(handler)` is called
- THEN the handler MUST receive two decoded messages in order

#### Scenario: Thread-safe writes
- GIVEN a `StdioTransport` with multiple threads calling `send()` concurrently
- WHEN all sends complete
- THEN the output stream MUST contain complete, non-interleaved frames for every send call

### Requirement: StdioTransport max message size

The `StdioTransport` MUST enforce a configurable maximum message size to prevent unbounded memory allocation.

#### Scenario: Default max size
- GIVEN a `StdioTransport` constructed with default settings
- THEN the max message size MUST be 10 MB (10,485,760 bytes)

#### Scenario: Oversized frame rejected
- GIVEN a `StdioTransport` with `maxMessageSize=100`
- AND input containing `Content-Length: 200\r\n\r\n` followed by 200 bytes
- WHEN `start(handler)` is called
- THEN `onError` MUST be called with an exception indicating the message exceeds the size limit
- AND the reader MUST continue processing subsequent frames

### Requirement: StdioTransport lifecycle

The `StdioTransport` MUST support start/stop lifecycle with graceful shutdown.

#### Scenario: Stop terminates reader
- GIVEN a running `StdioTransport`
- WHEN `stop()` is called
- THEN the background reader thread MUST terminate within 5 seconds

#### Scenario: Close releases resources
- GIVEN a `StdioTransport`
- WHEN `close()` is called
- THEN it MUST stop the reader if running and release stream resources

#### Scenario: Start after stop
- GIVEN a stopped `StdioTransport`
- WHEN `start(handler)` is called again
- THEN it MUST resume reading from the input stream

### Requirement: StdioTransport error recovery

The reader thread MUST recover from malformed frames and continue processing subsequent messages.

#### Scenario: Malformed header skipped
- GIVEN input containing invalid bytes (not a valid Content-Length header) followed by a valid frame
- WHEN the transport reads the input
- THEN `onError` MUST be called for the malformed portion
- AND the valid frame MUST still be delivered to the handler

#### Scenario: Stream end stops reader
- GIVEN a running transport whose input stream reaches end-of-stream
- WHEN EOF is detected
- THEN the reader thread MUST terminate gracefully
- AND `stop()` MUST complete without error

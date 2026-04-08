# Design: jsonrpc-e2e-tests

## Approach

Create a single JUnit 5 test class `JsonRpcEndToEndTest` in `org.specdriven.agent.jsonrpc` that wires together real `StdioTransport` and `JsonRpcDispatcher` instances. Tests use `ByteArrayInputStream`/`ByteArrayOutputStream` to simulate stdin/stdout I/O without spawning processes.

Each test:
1. Constructs a framed input stream containing one or more JSON-RPC requests
2. Creates a `StdioTransport` wrapping the input/output streams
3. Creates a `JsonRpcDispatcher` connected to the transport
4. Starts the transport (which reads frames and feeds the dispatcher)
5. Inspects the output stream for correctly framed JSON-RPC responses

A shared helper method `frame(String json)` builds Content-Length framed bytes (already used in `JsonRpcTransportTest`). A `parseResponses(String output)` helper extracts and decodes all response frames from the output.

## Key Decisions

- **No process spawning**: Tests run in-process using byte array streams. This avoids flaky process management while still exercising the full transport → dispatcher → SDK pipeline.
- **Reuse existing test patterns**: The `frame()` helper and `CollectingHandler` pattern from `JsonRpcTransportTest` and `CapturingTransport` from `JsonRpcDispatcherTest` are reused or adapted.
- **Synchronous verification**: The dispatcher handles most methods synchronously, so tests can assert output immediately. For `agent/run` (which is async), use a latch/timeout pattern matching `JsonRpcDispatcherTest`.
- **No new production code**: This change is test-only. If a test reveals a bug, it gets a separate fix.

## Alternatives Considered

- **Process-level E2E**: Spawning a real JVM process with stdin/stdout pipes. Ruled out — too slow and flaky for CI, and the in-process approach exercises all the same code paths.
- **Using a mocking framework**: Mocking the SDK or transport. Ruled out — the goal is to verify real integration, not unit behavior.

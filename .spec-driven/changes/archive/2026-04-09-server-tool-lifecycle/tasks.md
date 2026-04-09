# Tasks: server-tool-lifecycle

## Implementation

- [x] Define `ProbeType` enum in `org.specdriven.agent.tool` with values `TCP`, `HTTP`
- [x] Define `ReadyProbe` record in `org.specdriven.agent.tool` with all fields per spec (type, host, port, expectedStatus, timeout, retryInterval, maxRetries)
- [x] Define `ProbeStrategy` interface in `org.specdriven.agent.tool` with `probe(ReadyProbe)` returning boolean
- [x] Implement `TcpProbeStrategy` using `java.net.Socket` — connect, return true/false, close socket
- [x] Implement `HttpProbeStrategy` using `java.net.HttpURLConnection` — GET request, check status code
- [x] Define `ServerTool` interface extending `BackgroundTool` with `getReadyProbe()` method
- [x] Add `SERVER_TOOL_READY` and `SERVER_TOOL_FAILED` to `EventType` enum with required metadata
- [x] Add `registerWithProbe(Process, String, String, ReadyProbe)` method to `ProcessManager` interface
- [x] Refactor existing `register()` in `DefaultProcessManager` to delegate to `registerWithProbe()` with null probe
- [x] Store `ReadyProbe` in `DefaultProcessManager`'s internal process tracking (alongside ManagedProcess)
- [x] Implement `waitForReady(String processId, Duration timeout)` in `DefaultProcessManager` with retry loop using probe strategy
- [x] Implement `cleanup(String processId)` in `DefaultProcessManager` delegating to `stop()`
- [x] Add unit tests for `TcpProbeStrategy` using a local `ServerSocket`
- [x] Add unit tests for `HttpProbeStrategy` using a local `HttpServer`
- [x] Add unit tests for `waitForReady()` — probe succeeds, probe times out, unknown process, no probe
- [x] Add unit tests for `cleanup()` — delegates to stop, unknown process returns false
- [x] Add unit test verifying `SERVER_TOOL_READY` and `SERVER_TOOL_FAILED` events are emitted correctly

## Testing

- [x] Lint: run `mvn compile` and confirm zero compilation errors
- [x] Unit tests: run `mvn test -pl . -Dtest="org.specdriven.agent.tool.*Test"` and confirm all pass

## Verification

- [x] Verify all new types (`ProbeType`, `ReadyProbe`, `ProbeStrategy`, `TcpProbeStrategy`, `HttpProbeStrategy`, `ServerTool`) exist and match spec
- [x] Verify `ProcessManager` interface has new methods (`registerWithProbe`, `waitForReady`, `cleanup`)
- [x] Verify `DefaultProcessManager` implements all new methods correctly
- [x] Verify `EventType` enum includes `SERVER_TOOL_READY` and `SERVER_TOOL_FAILED`
- [x] Verify existing `ProcessManager` tests still pass (no regression)

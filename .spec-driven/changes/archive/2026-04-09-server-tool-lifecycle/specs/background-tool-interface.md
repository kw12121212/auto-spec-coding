# Background Tool Interface Spec (Delta)

## ADDED Requirements

### Requirement: ServerTool interface

- MUST be a public interface in `org.specdriven.agent.tool` that extends `BackgroundTool`
- MUST define `getReadyProbe()` returning `ReadyProbe`
- A tool implementing `ServerTool` indicates it launches a server-class process that requires readiness probing before use

### Requirement: ReadyProbe record

- MUST be a Java record in `org.specdriven.agent.tool` with fields: `type` (ProbeType), `host` (String, default "localhost"), `port` (int), `path` (String, nullable, used only for HTTP probes), `expectedStatus` (int, default 200, used only for HTTP probes), `timeout` (Duration, default PT30S), `retryInterval` (Duration, default PT1S), `maxRetries` (int, default 30)
- MUST be immutable
- `type` MUST NOT be null

### Requirement: ProbeType enum

- MUST be an enum in `org.specdriven.agent.tool` with values: `TCP`, `HTTP`
- `TCP` represents a TCP connect probe
- `HTTP` represents an HTTP GET probe

### Requirement: ProbeStrategy interface

- MUST be a public interface in `org.specdriven.agent.tool`
- MUST define `probe(ReadyProbe probe)` returning `boolean` — true if the probe succeeds, false otherwise
- MUST NOT throw checked exceptions — probe failures MUST return false

### Requirement: TcpProbeStrategy implementation

- MUST implement `ProbeStrategy` in `org.specdriven.agent.tool`
- MUST attempt a TCP connection to `probe.host()`:`probe.port()` using `java.net.Socket`
- MUST return true if the connection is established within `probe.retryInterval()`
- MUST return false if the connection is refused or times out
- MUST close the socket immediately after a successful connection

### Requirement: HttpProbeStrategy implementation

- MUST implement `ProbeStrategy` in `org.specdriven.agent.tool`
- MUST send an HTTP GET to `http://<probe.host()>:<probe.port()><probe.path()>` (path defaults to "/" if null)
- MUST return true if the HTTP response status code equals `probe.expectedStatus()`
- MUST return false if the connection fails, times out, or returns a different status code
- MUST use `java.net.HttpURLConnection` — no external HTTP client dependency

### Requirement: ProcessManager readiness probing

- `ProcessManager` MUST define `waitForReady(String processId, Duration timeout)` returning `boolean`
- MUST return false immediately if the process ID is unknown
- MUST return false immediately if no `ReadyProbe` is associated with the process
- MUST return true immediately if the process state is already `COMPLETED` or `RUNNING` and the probe succeeds on first attempt
- MUST retry the probe at `probe.retryInterval()` intervals until either the probe succeeds, `maxRetries` is exhausted, or `timeout` elapses
- MUST return true if the probe succeeds within the timeout, false otherwise
- MUST NOT block the calling thread beyond the specified `timeout`

### Requirement: ProcessManager probe registration

- `ProcessManager` MUST define `registerWithProbe(Process process, String toolName, String command, ReadyProbe probe)` returning `BackgroundProcessHandle`
- MUST behave identically to `register()` but additionally store the `ReadyProbe` for later use by `waitForReady()`
- `register()` MUST delegate to `registerWithProbe()` with a null probe

### Requirement: ProcessManager cleanup

- `ProcessManager` MUST define `cleanup(String processId)` returning `boolean`
- MUST call `stop(processId)` internally
- MUST return the result of `stop(processId)`
- Reserved for future resource cleanup extensions (temp files, port release, etc.)

### Requirement: Server tool event types

- `EventType` enum MUST add two values: `SERVER_TOOL_READY`, `SERVER_TOOL_FAILED`
- `SERVER_TOOL_READY` events MUST carry metadata with keys: `processId` (String), `toolName` (String), `probeType` (String)
- `SERVER_TOOL_FAILED` events MUST carry metadata with keys: `processId` (String), `toolName` (String), `probeType` (String), `reason` (String)

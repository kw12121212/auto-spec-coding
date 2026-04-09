# Background Tool Interface Spec

## ADDED Requirements

### Requirement: BackgroundTool interface

- MUST be a public interface in `org.specdriven.agent.tool` that extends `Tool`
- MUST define `startBackground(ToolInput input, ToolContext context)` returning `ToolResult` — the synchronous `execute()` method MUST delegate to `startBackground()` and return the same result
- `execute()` MUST return `ToolResult.Success` whose output is the JSON-serialized `BackgroundProcessHandle` on successful launch
- `execute()` MUST return `ToolResult.Error` if the process fails to start
- MUST NOT block until process completion — MUST return immediately after launching the process

### Requirement: BackgroundProcessHandle record

- MUST be a Java record in `org.specdriven.agent.tool` with fields: `id` (String, UUID), `pid` (long, OS process ID, -1 if unavailable), `command` (String), `toolName` (String), `startTime` (long, epoch millis), `state` (ProcessState)
- `id` MUST be a randomly generated UUID on construction when null or blank
- MUST be immutable

### Requirement: ProcessState enum

- MUST be an enum in `org.specdriven.agent.tool` with values: `STARTING`, `RUNNING`, `COMPLETED`, `FAILED`, `STOPPED`
- `STARTING` represents a process that has been requested but not yet confirmed running
- `RUNNING` represents an actively executing process
- `COMPLETED` represents a process that exited normally (exit code 0)
- `FAILED` represents a process that exited with a non-zero exit code
- `STOPPED` represents a process that was terminated by user or system action

### Requirement: ProcessOutput record

- MUST be a Java record in `org.specdriven.agent.tool` with fields: `stdout` (String), `stderr` (String), `exitCode` (int, -1 if still running), `timestamp` (long, epoch millis)
- MUST be immutable
- Represents a point-in-time snapshot of a background process output

### Requirement: BackgroundTool EventType extensions

- `EventType` enum MUST add two values: `BACKGROUND_TOOL_STARTED`, `BACKGROUND_TOOL_STOPPED`
- `BACKGROUND_TOOL_STARTED` events MUST carry metadata with keys: `processId` (String), `toolName` (String), `command` (String)
- `BACKGROUND_TOOL_STOPPED` events MUST carry metadata with keys: `processId` (String), `exitCode` (int as String)

### Requirement: ProcessManager interface

- MUST be a public interface in `org.specdriven.agent.tool`
- MUST define `register(Process process, String toolName, String command)` returning `BackgroundProcessHandle` — registers an already-launched process for lifecycle management
- MUST define `getState(String processId)` returning `Optional<ProcessState>` — returns empty if process ID is unknown
- MUST define `getOutput(String processId)` returning `Optional<ProcessOutput>` — returns a point-in-time snapshot of accumulated stdout, stderr, exit code, and timestamp; returns empty if process ID is unknown
- MUST define `listActive()` returning `List<BackgroundProcessHandle>` — all processes in `STARTING` or `RUNNING` state
- MUST define `stop(String processId)` returning `boolean` — true if the process was found and a stop signal was sent; false if the process ID is unknown or already terminated
- MUST define `stopAll()` returning `int` — the count of processes that were successfully stopped
- MUST define `registerWithProbe(Process process, String toolName, String command, ReadyProbe probe)` returning `BackgroundProcessHandle` — behaves identically to `register()` but additionally stores the `ReadyProbe` for later use by `waitForReady()`
- MUST define `waitForReady(String processId, Duration timeout)` returning `boolean` — returns false immediately if the process ID is unknown or no probe is associated; retries the probe at `retryInterval` until success, `maxRetries`, or `timeout`; returns true if probe succeeds
- MUST define `cleanup(String processId)` returning `boolean` — calls `stop()` internally, reserved for future resource cleanup

### Requirement: DefaultProcessManager implementation

- MUST implement `ProcessManager` in `org.specdriven.agent.tool`
- MUST accept `EventBus` as a constructor parameter (required for event emission)
- MUST accept `maxOutputBytes` as an optional constructor parameter (default 1MB = 1048576 bytes)
- MUST use `ConcurrentHashMap` for thread-safe process tracking
- MUST be a public class

### Requirement: Process registration behavior

- `register()` MUST create a `BackgroundProcessHandle` with a generated UUID, the OS process PID (via `Process.pid()`), the provided tool name and command, current epoch millis, and `ProcessState.STARTING`
- `register()` MUST start a virtual thread to read stdout and another to read stderr, buffering into separate ring buffers capped at `maxOutputBytes`
- `register()` MUST start a virtual thread that monitors `Process.toHandle().onExit()` and updates the process state to `COMPLETED` (exit code 0) or `FAILED` (non-zero exit code) on termination
- `register()` MUST emit a `BACKGROUND_TOOL_STARTED` event via the provided `EventBus` with metadata: `processId`, `toolName`, `command`
- `register()` MUST update the process state from `STARTING` to `RUNNING` once output readers are active
- `register()` MUST return the `BackgroundProcessHandle` immediately without blocking

### Requirement: Output ring buffer

- stdout and stderr MUST each be buffered independently
- When buffer size exceeds `maxOutputBytes`, oldest content MUST be discarded to stay within the limit (tail-truncation)
- `getOutput()` MUST return the current buffer contents as UTF-8 strings
- `getOutput()` for a running process MUST return exit code -1
- `getOutput()` for a terminated process MUST return the actual exit code

### Requirement: Process stop behavior

- `stop()` MUST call `ProcessHandle.destroy()` (graceful SIGTERM) as the first attempt
- `stop()` MUST update process state to `STOPPED`
- `stop()` MUST emit a `BACKGROUND_TOOL_STOPPED` event with metadata: `processId`, `exitCode`
- `stop()` MUST stop the output reader virtual threads for the process
- `stopAll()` MUST call `stop()` for every process in `STARTING` or `RUNNING` state and return the count of successfully stopped processes
- After a process exits naturally (not via `stop()`), the `ManagedProcess` entry MUST remain accessible via `getOutput()` and `getState()` until explicitly removed or the `ProcessManager` is shut down

### Requirement: Thread safety

- All `ProcessManager` methods MUST be safe for concurrent access from multiple threads
- `listActive()` MUST return a snapshot list — modifications to the returned list MUST NOT affect internal state

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

- `waitForReady(String processId, Duration timeout)` MUST return false immediately if the process ID is unknown
- `waitForReady()` MUST return false immediately if no `ReadyProbe` is associated with the process
- `waitForReady()` MUST retry the probe at `probe.retryInterval()` intervals until either the probe succeeds, `maxRetries` is exhausted, or `timeout` elapses
- `waitForReady()` MUST return true if the probe succeeds within the timeout, false otherwise
- `waitForReady()` MUST NOT block the calling thread beyond the specified `timeout`

### Requirement: ProcessManager probe registration

- `registerWithProbe(Process process, String toolName, String command, ReadyProbe probe)` MUST behave identically to `register()` but additionally store the `ReadyProbe` for later use by `waitForReady()`
- `register()` MUST delegate to `registerWithProbe()` with a null probe

### Requirement: ProcessManager cleanup

- `cleanup(String processId)` MUST call `stop(processId)` internally
- `cleanup()` MUST return the result of `stop(processId)`
- Reserved for future resource cleanup extensions (temp files, port release, etc.)

### Requirement: Server tool event types

- `EventType` enum MUST include `SERVER_TOOL_READY` and `SERVER_TOOL_FAILED`
- `SERVER_TOOL_READY` events MUST carry metadata with keys: `processId` (String), `toolName` (String), `probeType` (String)
- `SERVER_TOOL_FAILED` events MUST carry metadata with keys: `processId` (String), `toolName` (String), `probeType` (String), `reason` (String)

# Background Tool Interface Spec — process-manager delta

## ADDED Requirements

### Requirement: ProcessManager interface

- MUST be a public interface in `org.specdriven.agent.tool`
- MUST define `register(Process process, String toolName, String command)` returning `BackgroundProcessHandle` — registers an already-launched process for lifecycle management
- MUST define `getState(String processId)` returning `Optional<ProcessState>` — returns empty if process ID is unknown
- MUST define `getOutput(String processId)` returning `Optional<ProcessOutput>` — returns a point-in-time snapshot of accumulated stdout, stderr, exit code, and timestamp; returns empty if process ID is unknown
- MUST define `listActive()` returning `List<BackgroundProcessHandle>` — all processes in `STARTING` or `RUNNING` state
- MUST define `stop(String processId)` returning `boolean` — true if the process was found and a stop signal was sent; false if the process ID is unknown or already terminated
- MUST define `stopAll()` returning `int` — the count of processes that were successfully stopped

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

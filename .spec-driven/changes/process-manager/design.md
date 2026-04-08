# Design: process-manager

## Approach

Introduce a `ProcessManager` interface with a `DefaultProcessManager` implementation that manages a concurrent map of registered processes. Each registered process wraps a `java.lang.Process` and provides streaming output collection via virtual threads.

### ProcessManager interface

```java
public interface ProcessManager {
    BackgroundProcessHandle register(Process process, String toolName, String command);
    Optional<ProcessState> getState(String processId);
    Optional<ProcessOutput> getOutput(String processId);
    List<BackgroundProcessHandle> listActive();
    boolean stop(String processId);
    int stopAll();
}
```

### DefaultProcessManager

- Backed by `ConcurrentHashMap<String, ManagedProcess>` where `ManagedProcess` is an internal holder for the `Process`, `BackgroundProcessHandle`, and output buffers.
- `register()` creates a `BackgroundProcessHandle`, starts virtual thread output readers, and emits `BACKGROUND_TOOL_STARTED`.
- Output readers use a ring buffer capped at configurable max bytes (default 1MB). When the buffer overflows, oldest bytes are discarded (tail-truncation — keeps the most recent output).
- State monitoring: on `register()`, a virtual thread watches `Process.onExit()` and updates state + emits `BACKGROUND_TOOL_STOPPED` when the process exits.
- `stop()` uses `ProcessHandle.destroy()` for graceful stop, `ProcessHandle.destroyForcibly()` after a configurable grace period.

### Output buffering strategy

- Each process gets a ring buffer capped at `maxOutputBytes` (default 1MB = 1048576 bytes).
- When the buffer is full, oldest bytes are discarded, keeping only the tail (most recent output).
- `getOutput()` returns a snapshot `ProcessOutput` with current stdout, stderr, exitCode, and timestamp.

## Key Decisions

1. **JDK 25 ProcessHandle API** — use `Process.toHandle()`, `ProcessHandle.onExit()`, `ProcessHandle.destroy()` for native process lifecycle management.
2. **Ring buffer for output** — avoids unbounded memory growth. Default 1MB per process is sufficient for most tool output while preventing OOM. Configurable via constructor parameter.
3. **Virtual threads for output reading** — each process gets two virtual threads (stdout, stderr) that read and buffer output. Lightweight and scalable.
4. **Event-driven state transitions** — process exit is detected via `onExit()` CompletableFuture, triggering state update and event emission.
5. **ProcessManager does not launch processes** — it only manages processes that are already created. Launching is the responsibility of `BackgroundTool` implementations.

## Alternatives Considered

- **Unbounded output buffer** — rejected due to OOM risk for long-running processes (explicitly called out in M15 risks).
- **File-backed output buffering** — adds I/O complexity and temp file cleanup burden. In-memory ring buffer is simpler and sufficient for the target use case.
- **Shared thread pool for output readers** — unnecessary complexity given virtual threads are essentially free.
- **Making ProcessManager part of ToolContext** — deferred to `background-tool-integ` change where Agent-ProcessManager integration is designed.

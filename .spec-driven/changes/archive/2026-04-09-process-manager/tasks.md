# Tasks: process-manager

## Implementation

- [x] Create `ProcessManager` interface in `org.specdriven.agent.tool` with methods: `register`, `getState`, `getOutput`, `listActive`, `stop`, `stopAll`
- [x] Create `RingBuffer` internal class for bounded output buffering with tail-truncation overflow strategy
- [x] Create `ManagedProcess` internal holder class wrapping `Process`, `BackgroundProcessHandle`, and ring buffers
- [x] Implement `DefaultProcessManager.register()` — create handle, start stdout/stderr virtual thread readers, start onExit monitor, emit BACKGROUND_TOOL_STARTED event
- [x] Implement `DefaultProcessManager.getState()` — lookup by process ID, return current ProcessState
- [x] Implement `DefaultProcessManager.getOutput()` — snapshot current ring buffer contents into ProcessOutput
- [x] Implement `DefaultProcessManager.listActive()` — filter to STARTING/RUNNING processes
- [x] Implement `DefaultProcessManager.stop()` — destroy process, update state, emit BACKGROUND_TOOL_STOPPED event, stop readers
- [x] Implement `DefaultProcessManager.stopAll()` — stop all active processes

## Testing

- [x] Lint: run `mvn compile -q` to verify zero compilation errors
- [x] Unit tests: run `mvn test -pl . -Dtest="org.specdriven.agent.tool.ProcessManagerTest" -Dsurefire.useFile=false`
- [x] Test `register()` creates handle with correct fields and emits BACKGROUND_TOOL_STARTED event
- [x] Test `getState()` returns correct state transitions: STARTING → RUNNING → COMPLETED/FAILED
- [x] Test `getOutput()` returns accumulated stdout/stderr for running and completed processes
- [x] Test output tail-truncation when buffer exceeds maxOutputBytes
- [x] Test `stop()` transitions to STOPPED and emits BACKGROUND_TOOL_STOPPED event
- [x] Test `stopAll()` stops multiple active processes and returns correct count
- [x] Test `listActive()` returns only STARTING/RUNNING processes
- [x] Test `getState()` and `getOutput()` return empty for unknown process IDs

## Verification

- [x] Verify all ProcessManager spec requirements are covered by tests
- [x] Verify event emission matches EventType spec (metadata keys: processId, toolName, command / processId, exitCode)
- [x] Verify no modifications to existing BackgroundTool, ProcessState, ProcessOutput types

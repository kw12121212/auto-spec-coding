# Tasks: process-manager

## Implementation

- [ ] Create `ProcessManager` interface in `org.specdriven.agent.tool` with methods: `register`, `getState`, `getOutput`, `listActive`, `stop`, `stopAll`
- [ ] Create `RingBuffer` internal class for bounded output buffering with tail-truncation overflow strategy
- [ ] Create `ManagedProcess` internal holder class wrapping `Process`, `BackgroundProcessHandle`, and ring buffers
- [ ] Implement `DefaultProcessManager.register()` — create handle, start stdout/stderr virtual thread readers, start onExit monitor, emit BACKGROUND_TOOL_STARTED event
- [ ] Implement `DefaultProcessManager.getState()` — lookup by process ID, return current ProcessState
- [ ] Implement `DefaultProcessManager.getOutput()` — snapshot current ring buffer contents into ProcessOutput
- [ ] Implement `DefaultProcessManager.listActive()` — filter to STARTING/RUNNING processes
- [ ] Implement `DefaultProcessManager.stop()` — destroy process, update state, emit BACKGROUND_TOOL_STOPPED event, stop readers
- [ ] Implement `DefaultProcessManager.stopAll()` — stop all active processes

## Testing

- [ ] Lint: run `mvn compile -q` to verify zero compilation errors
- [ ] Unit tests: run `mvn test -pl . -Dtest="org.specdriven.agent.tool.ProcessManagerTest" -Dsurefire.useFile=false`
- [ ] Test `register()` creates handle with correct fields and emits BACKGROUND_TOOL_STARTED event
- [ ] Test `getState()` returns correct state transitions: STARTING → RUNNING → COMPLETED/FAILED
- [ ] Test `getOutput()` returns accumulated stdout/stderr for running and completed processes
- [ ] Test output tail-truncation when buffer exceeds maxOutputBytes
- [ ] Test `stop()` transitions to STOPPED and emits BACKGROUND_TOOL_STOPPED event
- [ ] Test `stopAll()` stops multiple active processes and returns correct count
- [ ] Test `listActive()` returns only STARTING/RUNNING processes
- [ ] Test `getState()` and `getOutput()` return empty for unknown process IDs

## Verification

- [ ] Verify all ProcessManager spec requirements are covered by tests
- [ ] Verify event emission matches EventType spec (metadata keys: processId, toolName, command / processId, exitCode)
- [ ] Verify no modifications to existing BackgroundTool, ProcessState, ProcessOutput types

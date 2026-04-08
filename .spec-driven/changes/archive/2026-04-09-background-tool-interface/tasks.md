# Tasks: background-tool-interface

## Implementation

- [x] Create `ProcessState` enum in `org.specdriven.agent.tool` with values: STARTING, RUNNING, COMPLETED, FAILED, STOPPED
- [x] Create `ProcessHandle` record in `org.specdriven.agent.tool` with fields: id, pid, command, toolName, startTime, state
- [x] Create `ProcessOutput` record in `org.specdriven.agent.tool` with fields: stdout, stderr, exitCode, timestamp
- [x] Create `BackgroundTool` interface in `org.specdriven.agent.tool` extending `Tool` with `startBackground(ToolInput, ToolContext)` method; `execute()` delegates to `startBackground()`
- [x] Add `BACKGROUND_TOOL_STARTED` and `BACKGROUND_TOOL_STOPPED` to `EventType` enum

## Testing

- [x] Run `mvn compile -q` — lint/validation: verify all new types compile without errors
- [x] Write unit tests for `ProcessHandle` record construction and immutability
- [x] Write unit tests for `ProcessState` enum values
- [x] Write unit tests for `ProcessOutput` record construction
- [x] Write unit tests verifying `BackgroundTool.execute()` delegates to `startBackground()` and returns JSON-serialized `ProcessHandle`
- [x] Run `mvn test -q`

## Verification

- [x] Verify all new types are in `org.specdriven.agent.tool` package
- [x] Verify `BackgroundTool` extends `Tool` without modifying the `Tool` interface
- [x] Verify `EventType` enum includes the two new values
- [x] Verify no changes to existing `Tool`, `ToolResult`, `ToolInput`, `ToolContext`, or `DefaultOrchestrator`

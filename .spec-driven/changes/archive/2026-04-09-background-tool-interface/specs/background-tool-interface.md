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

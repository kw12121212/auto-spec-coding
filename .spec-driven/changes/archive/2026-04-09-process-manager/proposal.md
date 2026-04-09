# Proposal: process-manager

## What

Implement a `ProcessManager` that tracks, queries, and controls the lifecycle of background processes launched by `BackgroundTool` instances. Provides output collection with configurable buffering, process state tracking via JDK 25 `ProcessHandle` API, and graceful termination.

## Why

M15's `background-tool-interface` change defined the types (`BackgroundTool`, `BackgroundProcessHandle`, `ProcessState`, `ProcessOutput`) but left the actual process lifecycle management unimplemented. Without a `ProcessManager`, there is no way to:
- List all active background processes
- Retrieve accumulated stdout/stderr from a running process
- Stop a specific process by ID
- Bound output buffering to prevent OOM

This is the next dependency in the M15 chain: `server-tool-lifecycle` and `background-tool-integ` both require a working `ProcessManager`.

## Scope

- `ProcessManager` interface and `DefaultProcessManager` implementation in `org.specdriven.agent.tool`
- Process registration, deregistration, and state tracking
- Output collection via streaming readers with configurable per-process buffer limit (default 1MB, tail-truncation on overflow)
- Stop/destroy via JDK 25 `ProcessHandle` API
- Event emission (`BACKGROUND_TOOL_STARTED` / `BACKGROUND_TOOL_STOPPED`) via existing `EventBus`
- Unit tests for each capability

## Unchanged Behavior

- Existing `Tool`, `BackgroundTool`, `BackgroundProcessHandle`, `ProcessState`, `ProcessOutput` types remain unchanged
- `EventType` enum values `BACKGROUND_TOOL_STARTED` / `BACKGROUND_TOOL_STOPPED` already exist
- Event system (`EventBus`, `Event`) remains unchanged
- Agent lifecycle and orchestrator are not modified by this change

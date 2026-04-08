# background-tool-interface

## What

Define the `BackgroundTool` interface and `BackgroundProcessHandle` type that extend the existing Tool surface with asynchronous execution semantics. A `BackgroundTool` starts a process and returns immediately with a `BackgroundProcessHandle`, enabling the agent to continue operating while the process runs in the background.

## Why

The current `Tool.execute()` contract is synchronous — it blocks until completion and returns a `ToolResult`. Real-world agent workflows need to launch long-running processes (build servers, file watchers, dev servers, test runners) and continue interacting with the user or other tools while those processes run. M15 (Background Process Management) requires a foundational interface before the `ProcessManager`, server tool lifecycle, and agent integration can be built.

## Scope

- Define `BackgroundTool` interface extending `Tool` with an async execution contract
- Define `BackgroundProcessHandle` record representing a managed background process
- Define `ProcessState` enum for process lifecycle states
- Define `ProcessOutput` record for retrieved output snapshots
- Add `BACKGROUND_TOOL_STARTED` and `BACKGROUND_TOOL_STOPPED` to `EventType` enum
- Add `BackgroundTool` spec delta file

## Unchanged Behavior

- Existing `Tool` interface and all implementations remain unchanged
- `ToolResult` sealed type is not modified
- `DefaultOrchestrator` execution loop is not modified — it continues to call `Tool.execute()` synchronously; background tool awareness is deferred to `background-tool-integ`
- Agent lifecycle transitions are unchanged

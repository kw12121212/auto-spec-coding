# tool-bash

## What

Implement a BashTool that executes shell commands via `ProcessBuilder`, with timeout control using JDK 25 `ProcessHandle`, stdout/stderr capture, and integration with the M1 `PermissionProvider` hook for execution authorization.

## Why

Bash execution is the single most critical tool in the agent's tool surface — it enables the agent to run arbitrary commands, interact with the system, and chain operations. This is the first concrete Tool implementation in M2 and establishes the implementation pattern that `tool-file-ops`, `tool-grep`, and `tool-glob` will follow.

## Scope

- `BashTool` class implementing the `Tool` interface
- Parameters: `command` (string, required), `timeout` (integer, optional, default 120s), `workDir` (string, optional, defaults to ToolContext.workDir)
- Execution via `ProcessBuilder` with `/bin/bash -c <command>` (or OS shell equivalent)
- Timeout enforcement via `ProcessHandle.destroyForcibly()` after deadline
- stdout and stderr captured and returned in `ToolResult.Success`
- Permission check via `ToolContext.permissionProvider().check()` before execution
- Unit tests covering happy path, timeout, permission denial, and error scenarios

## Unchanged Behavior

- `Tool` interface, `ToolInput`, `ToolResult`, `ToolContext`, `ToolParameter` — no modifications to existing types
- `PermissionProvider`, `Permission`, `PermissionContext` — consumed as-is
- Event system — no changes (tool execution events are out of scope for this change)

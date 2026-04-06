# tool-glob

## What

Implement a file pattern matching tool (`GlobTool`) that searches for files matching glob patterns across directory trees, sorted by modification time.

## Why

GlobTool is the last remaining planned change in M2 (Tool Surface Basics). Three of four tools are already complete (BashTool, file ops, GrepTool). Completing GlobTool closes M2 and unblocks M3.

## Scope

- `GlobTool` implementing the `Tool` interface
- Glob pattern matching via `java.nio.file.PathMatcher`
- Directory tree walking via `Files.walk()`
- Results sorted by modification time (most recent first)
- Permission integration via `PermissionProvider`
- Parameters: `pattern` (required), `path` (optional), `head_limit` (optional)
- Unit tests covering happy path and error cases

## Unchanged Behavior

- No changes to existing tools or interfaces
- No changes to the `Tool`, `ToolInput`, `ToolResult`, or `ToolContext` contracts

# tool-file-ops

## What

Implement file read, write, and edit tools that conform to the M1 Tool interface, providing core file I/O capabilities for the agent tool surface.

Three tools will be delivered:
- **read** — read file contents, optionally a line range
- **write** — create or overwrite a file with given content
- **edit** — perform exact string replacement within an existing file

## Why

File operations are the most fundamental tool type after bash execution. The agent needs to read source code, write new files, and make targeted edits. This is the next planned change in M2 after `tool-bash`, and subsequent tools (grep, glob) build on the same Tool interface patterns established here.

## Scope

In scope:
- `ReadTool` — read file contents with optional line offset/limit
- `WriteTool` — write content to a file (create or overwrite)
- `EditTool` — exact string replacement in an existing file
- Permission checks via `PermissionProvider` for read/write/edit actions
- Unit tests for all three tools (happy path + error cases)

Out of scope:
- Directory listing (covered by glob tool in separate change)
- File search (covered by grep tool in separate change)
- File watching / change notifications
- Symbolic link handling beyond basic resolution
- Binary file operations

## Unchanged Behavior

- Tool interface contract (M1) remains unchanged
- PermissionProvider interface remains unchanged
- BashTool behavior remains unchanged

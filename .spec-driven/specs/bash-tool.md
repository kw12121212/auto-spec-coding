# Bash Tool Spec

## ADDED Requirements

### Requirement: BashTool identity

- MUST return `"bash"` from `getName()`
- MUST return a non-empty description from `getDescription()`
- MUST declare parameters: `command` (string, required), `timeout` (integer, optional), `workDir` (string, optional)

### Requirement: BashTool execution

- MUST execute the `command` parameter via the system shell (`/bin/bash -c` on Linux, `sh -c` as fallback)
- MUST capture and return the combined stdout and stderr output in `ToolResult.Success`
- MUST use the `workDir` parameter as working directory when provided; otherwise MUST use `ToolContext.workDir()`
- MUST use the `timeout` parameter (in seconds) when provided; otherwise MUST default to 120 seconds
- MUST terminate the process and return `ToolResult.Error` with a timeout message if the process exceeds the configured timeout
- MUST use `ProcessHandle.destroyForcibly()` for timeout termination

### Requirement: BashTool permission integration

- MUST call `ToolContext.permissionProvider().check()` before execution
- MUST construct the Permission with `action="execute"`, `resource="bash"`, and `constraints` containing the command string
- MUST return `ToolResult.Error` without executing if the permission check returns `false`

### Requirement: BashTool error handling

- MUST return `ToolResult.Error` if `command` parameter is missing or empty
- MUST return `ToolResult.Error` if the process fails to start
- MUST return `ToolResult.Error` with the process exit message if the process exits with a non-zero code

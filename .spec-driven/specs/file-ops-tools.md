# File Ops Tools Spec

## ADDED Requirements

### Requirement: ReadTool identity

- MUST return `"read"` from `getName()`
- MUST return a non-empty description from `getDescription()`
- MUST declare parameters: `path` (string, required), `offset` (integer, optional), `limit` (integer, optional)

### Requirement: ReadTool execution

- MUST read the file at `path` and return its contents in `ToolResult.Success`
- MUST resolve relative paths against `ToolContext.workDir()`
- MUST use the `offset` parameter (1-based line number) as the starting line when provided
- MUST use the `limit` parameter to restrict the number of lines returned when provided
- MUST return `ToolResult.Error` if the file does not exist or is not readable

### Requirement: WriteTool identity

- MUST return `"write"` from `getName()`
- MUST return a non-empty description from `getDescription()`
- MUST declare parameters: `path` (string, required), `content` (string, required)

### Requirement: WriteTool execution

- MUST write `content` to the file at `path`, creating the file if it does not exist
- MUST create parent directories if they do not exist
- MUST overwrite existing file content
- MUST resolve relative paths against `ToolContext.workDir()`
- MUST return `ToolResult.Success` with a confirmation message on success

### Requirement: EditTool identity

- MUST return `"edit"` from `getName()`
- MUST return a non-empty description from `getDescription()`
- MUST declare parameters: `path` (string, required), `old_string` (string, required), `new_string` (string, required)

### Requirement: EditTool execution

- MUST read the file at `path`, replace the first occurrence of `old_string` with `new_string`, and write the result back
- MUST return `ToolResult.Error` if `old_string` is not found in the file content
- MUST resolve relative paths against `ToolContext.workDir()`
- MUST return `ToolResult.Success` with a confirmation message on success

### Requirement: File ops permission integration

- ReadTool MUST call `ToolContext.permissionProvider().check()` before reading, with `action="read"`, `resource` set to the resolved file path
- WriteTool MUST call `ToolContext.permissionProvider().check()` before writing, with `action="write"`, `resource` set to the resolved file path
- EditTool MUST call `ToolContext.permissionProvider().check()` before editing, with `action="edit"`, `resource` set to the resolved file path
- ReadTool MUST return `ToolResult.Error` without performing the operation when the permission decision is `PermissionDecision.DENY`
- ReadTool MUST return `ToolResult.Error` without performing the operation when the permission decision is `PermissionDecision.CONFIRM`, and the error message MUST indicate that explicit confirmation is required
- WriteTool and EditTool MUST return `ToolResult.Error` without performing the operation when the permission decision is `PermissionDecision.DENY`
- WriteTool and EditTool MUST return `ToolResult.Error` without performing the operation when the permission decision is `PermissionDecision.CONFIRM`, and the error message MUST indicate that explicit confirmation is required

### Requirement: File ops error handling

- All three tools MUST return `ToolResult.Error` if the `path` parameter is missing or empty
- WriteTool MUST return `ToolResult.Error` if `content` parameter is missing
- EditTool MUST return `ToolResult.Error` if `old_string` or `new_string` parameter is missing
- All three MUST return `ToolResult.Error` if an I/O error occurs during file operations

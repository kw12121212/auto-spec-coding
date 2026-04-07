# File Ops Tools Spec

## MODIFIED Requirements

### Requirement: File ops permission integration

- ReadTool MUST call `ToolContext.permissionProvider().check()` before reading, with `action="read"`, `resource` set to the resolved file path
- WriteTool MUST call `ToolContext.permissionProvider().check()` before writing, with `action="write"`, `resource` set to the resolved file path
- EditTool MUST call `ToolContext.permissionProvider().check()` before editing, with `action="edit"`, `resource` set to the resolved file path
- ReadTool MUST return `ToolResult.Error` without performing the operation when the permission decision is `PermissionDecision.DENY`
- ReadTool MUST return `ToolResult.Error` without performing the operation when the permission decision is `PermissionDecision.CONFIRM`, and the error message MUST indicate that explicit confirmation is required
- WriteTool and EditTool MUST return `ToolResult.Error` without performing the operation when the permission decision is `PermissionDecision.DENY`
- WriteTool and EditTool MUST return `ToolResult.Error` without performing the operation when the permission decision is `PermissionDecision.CONFIRM`, and the error message MUST indicate that explicit confirmation is required

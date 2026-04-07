# Glob Tool Spec

## MODIFIED Requirements

### Requirement: GlobTool permission integration

- MUST call `ToolContext.permissionProvider().check()` before execution
- MUST construct the Permission with `action="search"`, `resource` set to the resolved search root path, and empty constraints
- MUST return `ToolResult.Error` without executing when the permission decision is `PermissionDecision.DENY`
- MUST return `ToolResult.Error` without executing when the permission decision is `PermissionDecision.CONFIRM`, and the error message MUST indicate that explicit confirmation is required

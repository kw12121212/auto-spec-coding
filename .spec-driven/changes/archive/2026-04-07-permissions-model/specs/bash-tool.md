# Bash Tool Spec

## MODIFIED Requirements

### Requirement: BashTool permission integration

- MUST call `ToolContext.permissionProvider().check()` before execution
- MUST construct the Permission with `action="execute"`, `resource="bash"`, and `constraints` containing the command string
- MUST return `ToolResult.Error` without executing when the permission decision is `PermissionDecision.DENY`
- MUST return `ToolResult.Error` without executing when the permission decision is `PermissionDecision.CONFIRM`, and the error message MUST indicate that explicit confirmation is required

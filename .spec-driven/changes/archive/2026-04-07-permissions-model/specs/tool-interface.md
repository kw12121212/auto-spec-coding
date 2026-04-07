# Tool Interface Spec

## ADDED Requirements

### Requirement: Structured permission decision handling

- A tool that performs a permission check MUST treat `PermissionDecision.ALLOW` as permission granted and may continue execution
- A tool that performs a permission check MUST stop before performing the protected operation when the permission provider returns `PermissionDecision.DENY`
- A tool that performs a permission check MUST stop before performing the protected operation when the permission provider returns `PermissionDecision.CONFIRM`
- A tool stopped by `PermissionDecision.DENY` MUST return `ToolResult.Error` describing that permission was denied
- A tool stopped by `PermissionDecision.CONFIRM` MUST return `ToolResult.Error` describing that explicit confirmation is required

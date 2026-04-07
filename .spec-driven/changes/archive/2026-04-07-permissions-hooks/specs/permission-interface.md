# Permission Interface Spec (delta)

## MODIFIED Requirements

### Requirement: Structured permission decision handling

- ~~A tool that performs a permission check MUST treat `PermissionDecision.ALLOW` as permission granted and may continue execution~~
- ~~A tool that performs a permission check MUST stop before performing the protected operation when the permission provider returns `PermissionDecision.DENY`~~
- ~~A tool that performs a permission check MUST stop before performing the protected operation when the permission provider returns `PermissionDecision.CONFIRM`~~
- ~~A tool stopped by `PermissionDecision.DENY` MUST return `ToolResult.Error` describing that permission was denied~~
- ~~A tool stopped by `PermissionDecision.CONFIRM` MUST return `ToolResult.Error` describing that explicit confirmation is required~~

The orchestrator MUST invoke registered `ToolExecutionHook` instances before each tool execution
- A hook returning `ToolResult.Error` from `beforeExecute` MUST prevent the tool from being invoked
- A hook returning `null` from `beforeExecute` MUST allow execution to proceed to the next hook or the tool itself
- When the permission provider returns `PermissionDecision.DENY`, the `PermissionCheckHook` MUST return `ToolResult.Error` describing that permission was denied
- When the permission provider returns `PermissionDecision.CONFIRM`, the `PermissionCheckHook` MUST return `ToolResult.Error` describing that explicit confirmation is required
- When the permission provider returns `PermissionDecision.ALLOW`, the `PermissionCheckHook` MUST return `null` to allow execution

## ADDED Requirements

### Requirement: ToolExecutionHook interface

- MUST be a public interface in `org.specdriven.agent.hook`
- MUST define `ToolResult beforeExecute(Tool tool, ToolInput input, ToolContext context)` returning `null` to allow or `ToolResult.Error` to block
- MUST define `void afterExecute(Tool tool, ToolInput input, ToolResult result)` as a post-execution notification
- `afterExecute` MUST be called even when the tool returns `ToolResult.Error`
- `afterExecute` MUST NOT be called when `beforeExecute` blocks execution

### Requirement: PermissionCheckHook

- MUST implement `ToolExecutionHook`
- MUST be a public class in `org.specdriven.agent.hook`
- `beforeExecute` MUST call `tool.permissionFor(input)` to obtain the Permission, construct a `PermissionContext`, and delegate to `context.permissionProvider().check()`
- `afterExecute` MUST be a no-op

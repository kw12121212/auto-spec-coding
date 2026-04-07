# Tasks: permissions-hooks

## Implementation

- [x] Create `ToolExecutionHook` interface in `org.specdriven.agent.hook` with `beforeExecute` and `afterExecute`
- [x] Add `Permission permissionFor(ToolInput input)` default method to `Tool` interface
- [x] Create `PermissionCheckHook` class implementing `ToolExecutionHook`
- [x] Override `permissionFor()` in BashTool, ReadTool, WriteTool, EditTool, GrepTool, GlobTool — migrate existing Permission construction from `execute()`
- [x] Remove `PermissionChecks.check()` calls from all six tools
- [x] Delete `PermissionChecks` helper class
- [x] Update `OrchestratorConfig` to include `hooks` field (default empty list)
- [x] Modify `DefaultOrchestrator.executeToolCall()` to invoke hooks before/after tool execution
- [x] Wire `PermissionCheckHook` into `DefaultAgent.doExecute()` via `OrchestratorConfig`

## Testing

- [x] Lint / validate: `mvn compile`
- [x] Run unit tests: `mvn test`
- [x] Unit test: `PermissionCheckHook` returns null for ALLOW decision
- [x] Unit test: `PermissionCheckHook` returns ToolResult.Error for DENY decision
- [x] Unit test: `PermissionCheckHook` returns ToolResult.Error for CONFIRM decision
- [x] Unit test: `DefaultOrchestrator` skips tool execution when hook blocks
- [x] Unit test: `DefaultOrchestrator` runs tool execution when hook allows
- [x] Unit test: each tool's `permissionFor()` returns correct Permission for sample inputs

## Verification

- [x] Verify all existing tool tests pass without modification (observable behavior unchanged)
- [x] Verify permission denial still produces ToolResult.Error with correct message
- [x] Verify new hook tests cover ALLOW, DENY, CONFIRM paths

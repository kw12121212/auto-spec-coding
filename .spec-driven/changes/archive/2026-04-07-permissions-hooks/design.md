# Design: permissions-hooks

## Approach

### 1. ToolExecutionHook interface

Create a `ToolExecutionHook` functional-style interface in `org.specdriven.agent.hook`:

- `ToolResult beforeExecute(Tool tool, ToolInput input, ToolContext context)` — returns `null` to allow, or `ToolResult.Error` to block execution
- `void afterExecute(Tool tool, ToolInput input, ToolResult result)` — notification only, cannot alter the result

### 2. Permission-aware Tool extension

Add a default method to the `Tool` interface:

```java
default Permission permissionFor(ToolInput input) {
    return new Permission("execute", getName(), Map.of());
}
```

Each tool overrides this to declare its specific permission semantics (action, resource, constraints derived from input). This keeps permission knowledge in the tool while enforcement moves to the hook.

### 3. PermissionCheckHook

Implements `ToolExecutionHook`. In `beforeExecute`:
1. Calls `tool.permissionFor(input)` to get the Permission
2. Constructs `PermissionContext(tool.getName(), "execute", "agent")`
3. Calls `context.permissionProvider().check(permission, permissionContext)`
4. Returns `null` for ALLOW, `ToolResult.Error` for DENY or CONFIRM

`afterExecute` is a no-op.

### 4. Orchestrator integration

`DefaultOrchestrator.executeToolCall()` is modified to:
1. Run `hook.beforeExecute(tool, input, toolCtx)` for each registered hook
2. If any hook returns `ToolResult.Error`, skip tool execution and use that error
3. Execute `tool.execute(input, toolCtx)` only if all hooks allow
4. Run `hook.afterExecute(tool, input, result)` for each registered hook

`OrchestratorConfig` gains a `List<ToolExecutionHook> hooks()` field (default empty list).

### 5. Tool migration

Each tool that currently calls `PermissionChecks.check()`:
- Override `permissionFor(ToolInput input)` with the Permission construction logic already present in `execute()`
- Remove the `PermissionChecks.check()` call from `execute()`

Affected tools: BashTool, ReadTool, WriteTool, EditTool, GrepTool, GlobTool.

`PermissionChecks` class is deleted entirely.

## Key Decisions

- **Default method on Tool rather than separate interface**: Adding `permissionFor()` as a default method on `Tool` avoids an extra type and keeps the contract co-located. Tools that don't need custom permissions get a sensible default.
- **Hooks run in list order, first veto wins**: Simple sequential execution. If any hook returns an error, execution is blocked. This is predictable and easy to reason about.
- **Keep PermissionProvider on ToolContext**: The hook accesses the provider through `ToolContext.permissionProvider()`, same as the current per-tool pattern. No new wiring needed.

## Alternatives Considered

- **Keep per-tool checks, add coarse orchestrator gate**: Would double-check every call. Rejected as redundant and confusing.
- **Hook constructs Permission from tool name mapping**: Would couple the hook to specific tool implementations. Rejected — tools should own their permission semantics.
- **Separate PermissionAware interface**: Extra type with a single method. Rejected — a default method on Tool is simpler and avoids casting.

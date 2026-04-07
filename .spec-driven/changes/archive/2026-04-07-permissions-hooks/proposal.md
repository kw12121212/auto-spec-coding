# permissions-hooks

## What

Add a tool execution hook mechanism to the orchestrator that intercepts every tool call, and implement a permission check hook that enforces `PermissionProvider` decisions centrally. Migrate existing per-tool permission checks into the hook, removing the redundant `PermissionChecks` helper.

## Why

Currently each tool (BashTool, ReadTool, WriteTool, EditTool, GrepTool, GlobTool) performs its own permission check via the `PermissionChecks` helper inside `execute()`. This is fragile — a new tool can forget the check, and there is no single enforcement point. A hook-based design gives the orchestrator centralized control, guarantees every tool call is gated, and provides an extensibility point for future cross-cutting concerns (logging, rate limiting, metrics).

## Scope

- Define `ToolExecutionHook` interface with `beforeExecute` / `afterExecute`
- Define `PermissionCheckHook` implementing `ToolExecutionHook`
- Add `permissionFor(ToolInput)` default method to `Tool` interface so each tool declares its permission semantics
- Migrate per-tool permission construction from `execute()` into `permissionFor()` overrides
- Wire hook invocation into `DefaultOrchestrator.executeToolCall()`
- Remove `PermissionChecks` helper class
- Update `OrchestratorConfig` to accept a list of hooks
- Unit tests covering ALLOW, DENY, CONFIRM scenarios

## Unchanged Behavior

- Tools still deny permission-denied operations with `ToolResult.Error` — the observable behavior is identical, only the enforcement point moves
- `DefaultPermissionProvider` logic and its default policy rules remain unchanged
- `PermissionProvider`, `Permission`, `PermissionContext`, `PermissionDecision` types are not modified
- Tool execution results and ToolMessage content format remain the same

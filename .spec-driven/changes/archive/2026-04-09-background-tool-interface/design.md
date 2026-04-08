# Design: background-tool-interface

## Approach

Introduce `BackgroundTool` as a sub-interface of `Tool` that overrides the return semantics of `execute()`. A `BackgroundTool` still implements `execute(ToolInput, ToolContext)` but returns a `ToolResult.Success` whose output is the JSON-serialized `BackgroundProcessHandle`. This preserves the existing `Tool` → `ToolResult` contract so the orchestrator needs no changes.

The `BackgroundProcessHandle` record captures the OS process ID, the tool-assigned logical ID, the command, the start time, and the current `ProcessState`. Output retrieval and process control are handled by the `ProcessManager` (a later change), not by `BackgroundTool` itself.

Two new `EventType` values (`BACKGROUND_TOOL_STARTED`, `BACKGROUND_TOOL_STOPPED`) enable event-driven monitoring of background process lifecycle.

## Key Decisions

1. **BackgroundTool extends Tool rather than replacing it** — zero disruption to existing tools and orchestrator. The orchestrator treats `BackgroundTool` like any other `Tool`; the difference is only in the returned content.

2. **BackgroundProcessHandle is a data record, not a live handle** — it captures a snapshot of process metadata. Live operations (kill, read output) belong to `ProcessManager` in the next change. This keeps the interface layer pure and testable.

3. **BackgroundProcessHandle is returned as ToolResult.Success output (JSON)** — no new return type needed. The orchestrator, event system, and JSON-RPC/HTTP layers all work with `ToolResult` as-is.

4. **EventType extension** — two new enum values rather than a separate event system. Existing `EventBus` subscribers automatically receive background tool events if they subscribe to these types.

## Alternatives Considered

- **Separate `executeAsync` method returning `CompletableFuture<ToolResult>`** — rejected because it requires orchestrator changes and breaks the synchronous `Tool` contract. The orchestrator would need special-case logic for async tools.
- **New top-level `BackgroundTool` interface not extending `Tool`** — rejected because it fragments the tool registry and requires separate dispatch paths.
- **Embedding process control in BackgroundProcessHandle (kill, getOutput methods)** — rejected because it mixes data and behavior; process lifecycle management belongs in a dedicated `ProcessManager`.

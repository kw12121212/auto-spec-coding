# Design: tool-fd

## Approach

1. Add an optional `BuiltinToolManager` field to GlobTool, injected via constructor. A no-arg constructor remains for backward compatibility (uses pure Java path).
2. In `execute()`, after permission check and parameter validation, attempt fd execution before falling back to the existing `search()` method.
3. Build the fd command: `fd --glob <pattern> --absolute-path <search-root>` with optional `--max-results <n>`. Parse stdout as line-delimited absolute paths.
4. fd output does not guarantee modification-time ordering — apply post-sort if `head_limit` is not specified, or accept fd's default ordering when `head_limit` is set (fd's `--max-results` returns arbitrary-order matches). If the spec requires mtime sort, always post-sort and apply head_limit in Java.
5. If fd process fails (non-zero exit, exception, or not found), silently fall back to pure Java implementation.

## Key Decisions

- **Constructor injection for BuiltinToolManager**: Optional parameter, null means pure Java only. Keeps GlobTool backward-compatible and testable without a real BuiltinToolManager.
- **fd uses `--glob` flag**: Maps Java glob patterns directly to fd's glob engine. Patterns incompatible with fd glob syntax will trigger fd failure and fallback to pure Java.
- **Always post-sort by mtime**: fd's output order differs from the current mtime-descending sort. To maintain output consistency, always sort fd results by modification time in Java before applying head_limit, matching the pure Java behavior exactly.
- **Silent fallback on fd failure**: No error exposed to the caller — if fd is unavailable or fails, the tool works identically to the pure Java path.

## Alternatives Considered

- **BuiltinToolManager via ToolContext**: Would require changing the ToolContext interface, affecting all tools. Constructor injection is simpler and scoped to GlobTool only.
- **Always use fd when available, skip mtime sort**: Would change output ordering depending on whether fd is installed, breaking consistency. Rejected.
- **Separate FdTool class**: Would duplicate parameter definitions and add a new tool name. GlobTool with fd enhancement is simpler — same interface, better performance, transparent fallback.

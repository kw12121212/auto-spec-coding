# Design: tool-grep

## Approach

Follow the established pattern from `BashTool` and `ReadTool`: a single `GrepTool` class implementing `Tool`, with static parameter descriptors, permission check before execution, and pure Java implementation.

Use `Files.walk()` for directory traversal, `java.util.regex.Pattern` for regex matching, and `java.nio.file.PathMatcher` for the optional glob file filter. Results are accumulated in memory and returned as a formatted string in `ToolResult.Success`.

## Key Decisions

1. **Pure Java NIO over ripgrep** — M2 scope uses JDK APIs only; ripgrep binary integration belongs to M3's `builtin-tool-manager`. The Java implementation is sufficient for typical project-scale searches.
2. **Output modes** — Support three modes (`content`, `files_with_matches`, `count`) matching the Go implementation's behavior. The `content` mode includes line numbers and optional context lines.
3. **Symlink handling** — Skip symlink targets during walk (`FOLLOW_LINKS` not used) to avoid infinite loops and unintended directory escapes.
4. **Binary file detection** — Skip files that contain null bytes in the first 8KB to avoid searching binary files.
5. **Default output mode** — `content` as default matches the most common use case (showing matching lines with context).

## Alternatives Considered

- **Delegate to ripgrep via BashTool** — Would create a circular dependency on BashTool and bypasses the Tool abstraction. Deferred to M3.
- **Stream-based output** — `ToolResult` returns a single string, so streaming is not applicable at this layer. Could be revisited if a streaming ToolResult variant is introduced.

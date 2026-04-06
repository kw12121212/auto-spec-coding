# Design: tool-file-ops

## Approach

Follow the same class-level pattern as BashTool: implement the `Tool` interface, define parameters as a static immutable list, validate required parameters early, check permissions before execution, and return `ToolResult.Success` or `ToolResult.Error`.

Three separate tool classes in `org.specdriven.agent.tool`:
- `ReadTool` — uses `java.nio.file.Files.readString()` with optional `BufferedReader` for line-range reads
- `WriteTool` — uses `java.nio.file.Files.writeString()`, creating parent directories as needed
- `EditTool` — reads the full file, performs `String.replaceFirst()` for exact old→new replacement, writes back only if the old string was found

All file paths are resolved against `ToolContext.workDir()` when relative.

## Key Decisions

1. **Three separate tools vs. one combined tool** — Separate tools keep parameter schemas simple and align with the single-responsibility pattern established by BashTool. Each tool has a clear, distinct action.

2. **Edit uses exact string match, not regex or line-based editing** — Exact string replacement is the safest and most predictable edit model. It matches the Claude Code edit tool behavior (old_string → new_string replacement).

3. **ReadTool returns raw file content** — No parsing, formatting, or transformation. The caller decides how to interpret the content. Line numbers are 1-based when offset/limit is used.

4. **WriteTool creates parent directories** — Avoids requiring a separate "mkdir" step. Uses `Files.createDirectories()` on the parent path.

5. **Permission action matches tool name** — `action="read"` for ReadTool, `action="write"` for WriteTool, `action="edit"` for EditTool. The `resource` field carries the file path.

## Alternatives Considered

- **Single FileTool with mode parameter** — Rejected: adds complexity to parameter validation and permission modeling; each mode has different required parameters.
- **Line-number-based edit** — Rejected: fragile when files change between reads and edits. Exact string match is more robust.
- **Scoped write (append mode)** — Rejected: not needed yet. Can be added as a separate tool or parameter if required.

# Glob Tool Spec (Delta: tool-fd)

## MODIFIED Requirements

### Requirement: GlobTool execution

- MUST search for files matching the glob `pattern` under the given `path` (or `ToolContext.workDir()` if not provided)
- MUST resolve relative paths against `ToolContext.workDir()`
- When a `BuiltinToolManager` is provided via constructor and `BuiltinTool.FD` is resolvable:
  - MUST attempt file search using the fd binary with `--glob` flag and `--absolute-path` flag
  - MUST pass `--max-results` flag when `head_limit` parameter is provided
  - MUST post-sort fd results by modification time (most recently modified first) to match pure Java output ordering
  - MUST silently fall back to pure Java traversal if the fd process fails for any reason
- When fd is not used (no BuiltinToolManager, fd not resolvable, or fd failure):
  - MUST walk the directory tree using `Files.walk()`, visiting files only (not directories)
  - MUST skip symbolic links during traversal
  - MUST compile `pattern` using `PathMatcher` with `glob:` syntax; MUST return `ToolResult.Error` if the pattern is invalid
  - MUST return matching file paths sorted by modification time (most recently modified first)
- MUST respect the `head_limit` parameter by returning at most that many results when provided
- MUST return file paths as absolute paths, one per line, in `ToolResult.Success`

### Requirement: GlobTool construction

- MUST provide a no-arg constructor that uses pure Java file traversal (backward compatible)
- MUST provide a constructor accepting `BuiltinToolManager` that attempts fd acceleration when available
- MUST accept null `BuiltinToolManager` and treat it identically to the no-arg constructor

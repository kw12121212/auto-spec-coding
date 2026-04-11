---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/tool/GlobTool.java
  tests:
    - src/test/java/org/specdriven/agent/tool/GlobToolTest.java
---

# Glob Tool Spec

## ADDED Requirements

### Requirement: GlobTool identity

- MUST return `"glob"` from `getName()`
- MUST return a non-empty description from `getDescription()`
- MUST declare parameters: `pattern` (string, required), `path` (string, optional), `head_limit` (integer, optional)

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

### Requirement: GlobTool permission integration

- MUST call `ToolContext.permissionProvider().check()` before execution
- MUST construct the Permission with `action="search"`, `resource` set to the resolved search root path, and empty constraints
- MUST return `ToolResult.Error` without executing when the permission decision is `PermissionDecision.DENY`
- MUST return `ToolResult.Error` without executing when the permission decision is `PermissionDecision.CONFIRM`, and the error message MUST indicate that explicit confirmation is required

### Requirement: GlobTool error handling

- MUST return `ToolResult.Error` if `pattern` parameter is missing or empty
- MUST return `ToolResult.Error` if `pattern` is not a valid glob pattern
- MUST return `ToolResult.Error` if the search root path does not exist or is not a directory
- MUST return `ToolResult.Error` if an I/O error occurs during file traversal

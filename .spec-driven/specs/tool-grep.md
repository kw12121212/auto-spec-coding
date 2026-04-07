# Grep Tool Spec

## ADDED Requirements

### Requirement: GrepTool identity

- MUST return `"grep"` from `getName()`
- MUST return a non-empty description from `getDescription()`
- MUST declare parameters: `pattern` (string, required), `path` (string, optional), `glob` (string, optional), `output_mode` (string, optional), `case_insensitive` (boolean, optional), `context` (integer, optional), `head_limit` (integer, optional)

### Requirement: GrepTool execution

- MUST search files under the given `path` (or `ToolContext.workDir()` if not provided) for lines matching the regex `pattern`
- MUST compile `pattern` as a `java.util.regex.Pattern`; MUST return `ToolResult.Error` if the pattern is invalid
- MUST resolve relative paths against `ToolContext.workDir()`
- MUST walk the directory tree using `Files.walk()`, visiting files only (not directories)
- MUST skip symbolic links during traversal
- MUST skip files that contain null bytes within the first 8192 bytes (binary file detection)
- MUST filter files by the `glob` parameter using `PathMatcher` when provided; MUST search all files when not provided
- MUST respect the `head_limit` parameter by stopping after the specified number of matching entries when provided

### Requirement: GrepTool output modes

- MUST support three output modes controlled by the `output_mode` parameter: `content`, `files_with_matches`, `count`
- MUST default to `content` mode when `output_mode` is not provided
- In `content` mode, MUST return matching lines with their file path and 1-based line number, formatted as `filePath:lineNumber:lineContent`
- In `content` mode, MUST include context lines (lines before and after the match) when the `context` parameter is provided, using format `filePath-lineNumber-lineContent` for context lines
- In `files_with_matches` mode, MUST return only the distinct file paths that contain at least one match, one per line
- In `count` mode, MUST return the match count per file, formatted as `filePath:count`, one per line

### Requirement: GrepTool case sensitivity

- MUST perform case-sensitive matching by default
- MUST perform case-insensitive matching when `case_insensitive` parameter is `true`

### Requirement: GrepTool permission integration

- MUST call `ToolContext.permissionProvider().check()` before execution
- MUST construct the Permission with `action="search"`, `resource` set to the resolved search root path, and empty constraints
- MUST return `ToolResult.Error` without executing when the permission decision is `PermissionDecision.DENY`
- MUST return `ToolResult.Error` without executing when the permission decision is `PermissionDecision.CONFIRM`, and the error message MUST indicate that explicit confirmation is required

### Requirement: GrepTool error handling

- MUST return `ToolResult.Error` if `pattern` parameter is missing or empty
- MUST return `ToolResult.Error` if `pattern` is not a valid regex
- MUST return `ToolResult.Error` if the search root path does not exist or is not a directory
- MUST return `ToolResult.Error` if an I/O error occurs during file traversal or reading
- MUST silently skip individual files that cannot be read (permission denied, etc.) without failing the entire search

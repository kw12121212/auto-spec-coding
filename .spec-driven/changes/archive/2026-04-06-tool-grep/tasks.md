# Tasks: tool-grep

## Implementation

- [x] Create `GrepTool.java` in `org.specdriven.agent.tool` implementing `Tool` interface
- [x] Implement parameter descriptors: `pattern` (required), `path`, `glob`, `output_mode`, `case_insensitive`, `context`, `head_limit` (all optional)
- [x] Implement directory walk with `Files.walk()`, skipping symlinks and binary files
- [x] Implement regex matching with `Pattern`/`Matcher`, supporting case-insensitive flag
- [x] Implement glob file filtering with `PathMatcher` when `glob` parameter is provided
- [x] Implement `content` output mode with file path, line numbers, and context lines
- [x] Implement `files_with_matches` output mode returning distinct file paths
- [x] Implement `count` output mode returning per-file match counts
- [x] Implement `head_limit` support to cap result entries
- [x] Implement permission check before search execution
- [x] Implement error handling: missing pattern, invalid regex, missing directory, I/O errors, silently skipping unreadable files

## Testing

- [x] `GrepToolTest.java`: test `getName()` returns `"grep"`
- [x] Test missing/empty `pattern` returns `ToolResult.Error`
- [x] Test invalid regex pattern returns `ToolResult.Error`
- [x] Test non-existent search path returns `ToolResult.Error`
- [x] Test permission denied returns `ToolResult.Error` without searching
- [x] Test `content` mode with matching lines showing `path:lineNumber:content`
- [x] Test `content` mode with context lines showing `path-lineNumber-content`
- [x] Test `files_with_matches` mode returns only file paths
- [x] Test `count` mode returns `path:count` per file
- [x] Test case-insensitive matching
- [x] Test glob file filtering
- [x] Test `head_limit` caps results
- [x] Test binary files are skipped
- [x] Test unreadable individual files are silently skipped

## Verification

- [x] `mvn compile` passes
- [x] `mvn test` passes with all new tests green
- [x] Delta spec `tool-grep.md` matches implemented behavior

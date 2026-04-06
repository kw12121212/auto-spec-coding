# Proposal: tool-grep

## What

Implement a content search tool (`GrepTool`) that searches file contents using regular expression patterns, walking directory trees with Java NIO `Files.walk` and `Pattern` matching.

## Why

Content search is a core capability for a coding agent. Together with `BashTool` (command execution), `ReadTool/WriteTool/EditTool` (file operations), and the upcoming `GlobTool` (file pattern matching), it completes the foundational tool surface. Without content search, the agent cannot locate code, configuration, or text across a project — a critical gap.

## Scope

- Single `GrepTool` class implementing the `Tool` interface
- Parameters: `pattern` (regex, required), `path` (search root, optional — defaults to workDir), `glob` (file filter, optional), `output_mode` (content/files_with_matches/count, optional — defaults to content), `case_insensitive` (boolean, optional), `context` (integer, optional), `head_limit` (integer, optional)
- Permission check before execution with `action="search"`, `resource` set to search root path
- Pure Java NIO implementation (no external ripgrep dependency)
- Unit tests covering happy path, error cases, and edge cases

## Unchanged Behavior

- Existing tools (`BashTool`, `ReadTool`, `WriteTool`, `EditTool`) are not modified
- `Tool` interface, `ToolResult`, `ToolInput`, `ToolContext` remain unchanged
- `PermissionProvider` interface remains unchanged

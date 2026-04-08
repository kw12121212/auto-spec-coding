# tool-fd

## What

Modify GlobTool to use the fd binary (via BuiltinToolManager) for file pattern matching when available, falling back to the existing pure Java `Files.walk` implementation when fd is not installed or not extractable.

## Why

M3 milestone has `builtin-tool-manager` complete with `BuiltinTool.FD` already defined. The GlobTool currently uses a pure Java implementation which is slower for large directory trees. fd provides significantly faster file finding. This is the last planned change in M3 — completing it closes the milestone.

## Scope

- GlobTool constructor accepts optional `BuiltinToolManager` for fd resolution
- GlobTool attempts fd execution first when `BuiltinToolManager` is provided and fd is available
- GlobTool falls back to pure Java `Files.walk` when fd is unavailable or fails
- fd command-line flags mapped from GlobTool parameters (`pattern` → `--glob`, `path` → search root, `head_limit` → `--max-results`)
- Unit tests covering fd-available and fd-unavailable paths
- fd output format normalized to match existing GlobTool output (absolute paths, one per line, sorted by modification time)

## Unchanged Behavior

- GlobTool parameter interface (`pattern`, `path`, `head_limit`) remains identical
- GlobTool permission check logic and error messages unchanged
- BuiltinToolManager interface and DefaultBuiltinToolManager implementation unchanged
- When fd is unavailable, output is identical to current pure Java output
- BuiltinTool enum values unchanged

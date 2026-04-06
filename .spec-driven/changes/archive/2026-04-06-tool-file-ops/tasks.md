# Tasks: tool-file-ops

## Implementation

- [x] Create `ReadTool` class implementing Tool interface (name="read", parameters: path, offset, limit)
- [x] Create `WriteTool` class implementing Tool interface (name="write", parameters: path, content)
- [x] Create `EditTool` class implementing Tool interface (name="edit", parameters: path, old_string, new_string)
- [x] Add permission checks to all three tools (read/write/edit actions with file path as resource)
- [x] Create delta spec `file-ops-tools.md` in changes/tool-file-ops/specs/

## Testing

- [x] ReadToolTest: read full file, read line range, missing file error, missing path param error, permission denied
- [x] WriteToolTest: create new file, overwrite existing, create with parent dirs, missing params error, permission denied
- [x] EditToolTest: successful replacement, old string not found error, missing params error, permission denied
- [x] All existing tests still pass

## Verification

- [x] Verify ReadTool/WriteTool/EditTool conform to Tool interface spec
- [x] Verify delta spec matches actual implementation
- [x] Run `rtk mvn test` and confirm all tests pass

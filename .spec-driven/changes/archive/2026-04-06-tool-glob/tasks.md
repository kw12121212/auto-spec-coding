# Tasks: tool-glob

## Implementation

- [x] Create `GlobTool.java` implementing `Tool` interface with identity methods (`getName`, `getDescription`, `getParameters`)
- [x] Implement glob pattern matching using `PathMatcher` and `Files.walk()` with symlink skipping
- [x] Implement modification-time sort order (most recent first)
- [x] Implement `head_limit` parameter support
- [x] Implement permission integration (check before execution)
- [x] Implement error handling (missing pattern, invalid glob, bad path, I/O errors)

## Testing

- [x] Create `GlobToolTest.java` — happy path: find files matching a glob pattern
- [x] Test: results sorted by modification time
- [x] Test: `head_limit` caps the number of returned paths
- [x] Test: returns error when pattern is missing or empty
- [x] Test: returns error when search path does not exist
- [x] Test: returns error when permission denied
- [x] Test: skips symbolic links

## Verification

- [x] Run full test suite (`mvn test`) — all tests pass
- [x] Delta spec matches implementation

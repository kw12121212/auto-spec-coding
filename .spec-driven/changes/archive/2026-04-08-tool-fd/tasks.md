# Tasks: tool-fd

## Implementation

- [x] Add optional `BuiltinToolManager` constructor parameter to GlobTool (keep no-arg constructor for backward compatibility)
- [x] Implement `searchWithFd()` method: build fd command line (`fd --glob <pattern> --absolute-path <path> [--max-results <n>]`), execute process, parse stdout
- [x] Add post-sort by modification time to fd results to match pure Java output ordering
- [x] Wire fd attempt into `execute()`: try fd first when BuiltinToolManager is non-null, fall back to `search()` on any failure
- [x] Update delta spec `tool-glob.md` with fd enhancement requirements

## Testing

- [x] Lint/validation: `mvn compile`
- [x] Unit tests: `mvn test -Dtest=GlobToolTest`
- [x] Unit test: GlobTool with fd available — verify fd is invoked and output matches expected format
- [x] Unit test: GlobTool with fd unavailable — verify silent fallback to pure Java, output identical to no-BuiltinToolManager constructor
- [x] Unit test: GlobTool with fd returning error — verify fallback behavior
- [x] Unit test: GlobTool no-arg constructor — verify pure Java path unchanged

## Verification

- [x] Verify GlobTool parameter interface unchanged (pattern, path, head_limit)
- [x] Verify permission check still executes before fd attempt
- [x] Verify fd-unavailable path produces identical output to current implementation
- [x] Run `mvn test` to verify no regressions across the full test suite

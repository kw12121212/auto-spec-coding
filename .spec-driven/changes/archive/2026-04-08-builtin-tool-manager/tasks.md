# Tasks: builtin-tool-manager

## Implementation

- [x] Define `BuiltinTool` enum in `org.specdriven.agent.tool.builtin` with constants RG, FD and metadata fields (binaryName, versionTag)
- [x] Implement `Platform` record (os, arch) with static `detect()` method using system properties
- [x] Define `BuiltinToolManager` interface with `resolve(BuiltinTool)`, `detect(BuiltinTool)`, and `cacheDir()` methods
- [x] Implement `DefaultBuiltinToolManager`:
  - cache directory management (default `~/.specdriven/bin/`, configurable via constructor)
  - `detect()` — check cache then PATH, return Optional<Path>, no extraction
  - `resolve()` — detect first, extract from classpath if missing, return Path
  - PATH detection via `ProcessBuilder` (which/where)
  - classpath resource extraction to cache dir with executable permissions
- [x] Wire `BuiltinToolManager` into `SdkBuilder` as optional configuration (`.builtinToolManager(manager)`)

## Testing

- [x] Lint/validation: run `mvn compile` — verify zero compilation errors
- [x] Run `mvn test` — verify all new tests pass (15/15), existing tests unaffected
- [x] Unit test: `BuiltinToolTest` — verify enum constants have non-blank metadata
- [x] Unit test: `PlatformTest` — verify platform detection from system properties
- [x] Unit test: `DefaultBuiltinToolManagerTest` — verify detect/resolve with mocked classpath resources

## Verification

- [x] Verify all spec scenarios in `builtin-tool-manager.md` are covered by tests
- [x] Verify existing GrepTool/GlobTool tests still pass without modification
- [x] Verify `mvn test` passes with zero failures (excluding pre-existing unrelated failures)

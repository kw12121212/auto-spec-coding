# Tasks: builtin-tool-manager

## Implementation

- [ ] Define `BuiltinTool` enum in `org.specdriven.agent.tool.builtin` with constants RG, GH, FD and metadata fields (githubRepo, binaryName, versionTag)
- [ ] Implement `Platform` record (os, arch) with static `detect()` method using system properties
- [ ] Define `BuiltinToolManager` interface with `resolve(BuiltinTool)`, `detect(BuiltinTool)`, and `cacheDir()` methods
- [ ] Implement `DefaultBuiltinToolManager`:
  - cache directory management (default `~/.specdriven/bin/`, configurable via constructor)
  - `detect()` — check cache then PATH, return Optional<Path>, no download
  - `resolve()` — detect first, download if missing, return Path
  - PATH detection via `ProcessBuilder` (which/where)
- [ ] Implement `GitHubReleaseDownloader`:
  - fetch latest release metadata from GitHub API
  - select matching asset by platform tuple
  - download archive to temp file
  - extract binary from tar.gz / zip
  - SHA-256 checksum verification when checksum file is available in release assets
  - move extracted binary to cache with executable permissions
  - cleanup temp files on failure
- [ ] Wire `BuiltinToolManager` into `SdkBuilder` as optional configuration (`.builtinToolManager(manager)`)

## Testing

- [ ] Lint/validation: run `mvn compile` — verify zero compilation errors
- [ ] Run `mvn test` — verify all existing tests still pass
- [ ] Unit test: `BuiltinToolTest` — verify enum constants have non-blank metadata
- [ ] Unit test: `PlatformTest` — verify platform detection from system properties
- [ ] Unit test: `DefaultBuiltinToolManagerTest` — verify detect/resolve with mocked download
- [ ] Unit test: `GitHubReleaseDownloaderTest` — verify asset selection, checksum verification with mocked HTTP

## Verification

- [ ] Verify all spec scenarios in `builtin-tool-manager.md` are covered by tests
- [ ] Verify existing GrepTool/GlobTool tests still pass without modification
- [ ] Verify `mvn test` passes with zero failures

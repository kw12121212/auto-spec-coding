# builtin-tool-manager.md

## ADDED Requirements

### Requirement: BuiltinTool enum

The system MUST provide a `BuiltinTool` enum in `org.specdriven.agent.tool.builtin` with constants for each supported external tool. Each constant MUST carry metadata: tool name, GitHub owner/repo slug, binary name pattern, and version tag.

#### Scenario: Supported tools
- GIVEN the `BuiltinTool` enum
- THEN it MUST contain at least `RG` (ripgrep), `GH` (GitHub CLI), and `FD` (fd-find)
- AND each constant MUST return a non-blank `githubRepo()`, `binaryName()`, and `versionTag()`

#### Scenario: Tool binary name per platform
- GIVEN `BuiltinTool.RG` on a Linux x86_64 system
- WHEN `binaryName()` is called
- THEN it MUST return the platform-appropriate binary name (e.g., `rg`)

### Requirement: BuiltinToolManager interface

The system MUST provide a `BuiltinToolManager` interface in `org.specdriven.agent.tool.builtin` with methods for tool resolution.

#### Scenario: Resolve returns path
- GIVEN a `BuiltinToolManager` and `BuiltinTool.RG` where ripgrep is installed on the system
- WHEN `resolve(BuiltinTool.RG)` is called
- THEN it MUST return a `Path` pointing to the `rg` binary
- AND the path MUST point to an existing, executable file

#### Scenario: Resolve downloads if missing
- GIVEN a `BuiltinToolManager` and `BuiltinTool.RG` where ripgrep is NOT installed
- WHEN `resolve(BuiltinTool.RG)` is called
- THEN it MUST download the ripgrep binary from GitHub Releases
- AND install it to the local binary cache
- AND return the `Path` to the installed binary

#### Scenario: Detect without download
- GIVEN a `BuiltinToolManager`
- WHEN `detect(BuiltinTool.RG)` is called
- THEN it MUST return `Optional<Path>` — present if the tool is found on PATH or in cache, empty otherwise
- AND it MUST NOT trigger a download

### Requirement: Platform detection

The system MUST detect the current OS and architecture to select the correct release artifact.

#### Scenario: Linux x86_64 detection
- GIVEN a system where `os.name` contains "Linux" and `os.arch` is "amd64"
- WHEN platform is detected
- THEN it MUST identify the platform as (LINUX, X86_64)

#### Scenario: macOS ARM64 detection
- GIVEN a system where `os.name` contains "Mac" and `os.arch` is "aarch64"
- WHEN platform is detected
- THEN it MUST identify the platform as (MACOS, ARM64)

### Requirement: Local binary cache

The system MUST manage a local cache directory for downloaded tool binaries.

#### Scenario: Default cache directory
- GIVEN no explicit cache directory configured
- WHEN `BuiltinToolManager` is created
- THEN the cache directory MUST default to `~/.specdriven/bin/`

#### Scenario: Custom cache directory
- GIVEN a custom cache directory path `/opt/tools/`
- WHEN `BuiltinToolManager` is created with this path
- THEN it MUST use `/opt/tools/` as the cache directory

#### Scenario: Cache directory creation
- GIVEN a cache directory that does not exist
- WHEN a tool is resolved and downloaded
- THEN the directory MUST be created automatically (including parent directories)

### Requirement: Binary download from GitHub Releases

The system MUST download precompiled binaries from GitHub Releases.

#### Scenario: Download and extract
- GIVEN a tool not found locally
- WHEN `resolve(tool)` triggers a download
- THEN it MUST fetch the latest release from `api.github.com/repos/{owner}/{repo}/releases/latest`
- AND select the asset matching the current platform
- AND extract the binary from the archive (tar.gz or zip)
- AND place it in the cache directory with executable permissions

#### Scenario: Checksum verification
- GIVEN a downloaded archive with a published SHA-256 checksum
- WHEN the binary is extracted
- THEN the checksum MUST be verified before the binary is considered valid
- AND a checksum mismatch MUST cause the download to fail with a descriptive error

#### Scenario: Download failure
- GIVEN a network error during download
- WHEN `resolve(tool)` fails to download
- THEN it MUST throw an exception indicating the tool could not be acquired
- AND the partial download MUST be cleaned up

### Requirement: PATH detection fallback

The system MUST check the system PATH before downloading.

#### Scenario: Tool on PATH
- GIVEN `rg` is installed at `/usr/bin/rg`
- WHEN `resolve(BuiltinTool.RG)` is called
- THEN it MUST return `/usr/bin/rg` without downloading

#### Scenario: PATH detection uses OS-appropriate method
- GIVEN a Unix-like system
- WHEN checking PATH
- THEN it MUST use `which {binaryName}` equivalent logic
- GIVEN a Windows system
- WHEN checking PATH
- THEN it MUST use `where {binaryName}` equivalent logic

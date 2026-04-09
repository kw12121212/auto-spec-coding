# builtin-tool-manager.md

## ADDED Requirements

### Requirement: BuiltinTool enum

The system MUST provide a `BuiltinTool` enum in `org.specdriven.agent.tool.builtin` with constants for each supported external tool. Each constant MUST carry metadata: tool name, binary name, and version tag.

#### Scenario: Supported tools
- GIVEN the `BuiltinTool` enum
- THEN it MUST contain `RG` (ripgrep) and `FD` (fd-find)
- AND each constant MUST return a non-blank `binaryName()` and `versionTag()`

#### Scenario: Resource path per platform
- GIVEN `BuiltinTool.RG` and a Linux x86_64 platform
- WHEN `resourcePath(platform)` is called
- THEN it MUST return `builtin-tools/linux-x86_64/rg`

### Requirement: BuiltinToolManager interface

The system MUST provide a `BuiltinToolManager` interface in `org.specdriven.agent.tool.builtin` with methods for tool resolution.

#### Scenario: Resolve returns path
- GIVEN a `BuiltinToolManager` and `BuiltinTool.RG` where ripgrep is installed on the system
- WHEN `resolve(BuiltinTool.RG)` is called
- THEN it MUST return a `Path` pointing to the `rg` binary
- AND the path MUST point to an existing, executable file

#### Scenario: Resolve extracts from classpath if missing
- GIVEN a `BuiltinToolManager` and `BuiltinTool.RG` where ripgrep is NOT installed and NOT cached
- WHEN `resolve(BuiltinTool.RG)` is called
- AND the classpath contains the platform-specific resource `builtin-tools/{platform}/rg`
- THEN it MUST extract the binary from the classpath to the local cache
- AND return the `Path` to the extracted binary

#### Scenario: Detect without extraction
- GIVEN a `BuiltinToolManager`
- WHEN `detect(BuiltinTool.RG)` is called
- THEN it MUST return `Optional<Path>` — present if the tool is found on PATH or in cache, empty otherwise
- AND it MUST NOT trigger extraction from classpath

### Requirement: Platform detection

The system MUST detect the current OS and architecture to select the correct bundled resource.

#### Scenario: Linux x86_64 detection
- GIVEN a system where `os.name` contains "Linux" and `os.arch` is "amd64"
- WHEN platform is detected
- THEN it MUST identify the platform as (LINUX, X86_64)
- AND `resourceDir()` MUST return "linux-x86_64"

#### Scenario: macOS ARM64 detection
- GIVEN a system where `os.name` contains "Mac" and `os.arch` is "aarch64"
- WHEN platform is detected
- THEN it MUST identify the platform as (MACOS, ARM64)
- AND `resourceDir()` MUST return "macos-arm64"

### Requirement: Local binary cache

The system MUST manage a local cache directory for extracted tool binaries.

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
- WHEN a tool is resolved and extracted from classpath
- THEN the directory MUST be created automatically (including parent directories)

### Requirement: Classpath resource extraction

The system MUST extract precompiled binaries from classpath resources.

- When `resolve(BuiltinTool.RG)` needs to extract from the classpath, the application MUST package the platform-specific resource path returned by `BuiltinTool.RG.resourcePath(platform)` for the current supported build platform

#### Scenario: Extract and set executable
- GIVEN a tool binary in the classpath resources
- WHEN `resolve(tool)` triggers extraction
- THEN the binary MUST be copied from the classpath to the cache directory
- AND the binary MUST have executable permissions set

#### Scenario: Current platform bundled `rg` resource is packaged
- GIVEN the application resources for the current supported build platform
- WHEN `BuiltinTool.RG.resourcePath(Platform.detect())` is computed
- THEN the classpath MUST contain a readable resource at that exact path

#### Scenario: Resource not found
- GIVEN no bundled binary for the current platform on the classpath
- AND the tool is not on PATH
- WHEN `resolve(tool)` is called
- THEN it MUST throw a `BuiltinToolException` with a descriptive message

### Requirement: PATH detection fallback

The system MUST check the system PATH before extracting from classpath.

#### Scenario: Tool on PATH
- GIVEN `rg` is installed at `/usr/bin/rg`
- WHEN `resolve(BuiltinTool.RG)` is called
- THEN it MUST return `/usr/bin/rg` without extracting from classpath

#### Scenario: PATH detection uses OS-appropriate method
- GIVEN a Unix-like system
- WHEN checking PATH
- THEN it MUST use `which {binaryName}` equivalent logic

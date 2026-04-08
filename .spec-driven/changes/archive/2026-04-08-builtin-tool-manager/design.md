# Design: builtin-tool-manager

## Approach

Introduce a `BuiltinToolManager` that acts as a resolver for external tool binaries. The manager does not execute tools — it only ensures the binary is available on disk and returns its path.

1. **BuiltinTool enum** — Each supported external tool is an enum constant with metadata: tool name, binary name, and version tag. Resources are organized by platform directory.

2. **Platform detection** — Use `System.getProperty("os.name")` and `os.arch` to determine the current platform tuple (os, arch). Maps to a resource directory name (e.g. `linux-x86_64`, `macos-arm64`).

3. **Resolution flow** — `resolve(BuiltinTool)`:
   - Check local cache directory for an existing executable binary
   - If found, return its path
   - If not found, check system PATH via `which`/`where`
   - If found on PATH, return its path
   - If not found, extract from classpath resource to cache dir
   - Set executable permission on the extracted file
   - Return the path

4. **Resource extraction** — Copy the binary from classpath (`builtin-tools/{platform}/{binaryName}`) to the cache directory. No archive extraction needed — the binary is stored directly as a resource.

5. **Platform-specific packaging** — Binaries are bundled in platform-specific jars via Maven classifiers. The correct jar is selected at dependency resolution time.

## Key Decisions

- **Manager does not execute tools** — It only resolves binary paths. Tool implementations (GrepTool, etc.) decide whether to use the native binary or fall back to pure Java.
- **Cache directory default** — `~/.specdriven/bin/` keeps tool binaries in a predictable, user-local location. Overridable via constructor.
- **Classpath extraction, not runtime download** — Binaries are bundled at build time in platform-specific jars. No network dependency at runtime.
- **Version pinning via enum** — Each `BuiltinTool` constant carries a version string. Upgrading means updating the constant and replacing the resource.
- **No archive handling needed** — Binaries are stored as plain files in the classpath, not in archives. This simplifies extraction significantly.
- **Supported tools: RG and FD only** — `gh` (GitHub CLI) is not included as a builtin tool. Only ripgrep and fd-find are bundled.

## Alternatives Considered

- **Runtime download from GitHub Releases** — Would avoid bundling binaries in the jar but requires network access and handles rate limits. Rejected in favor of zero-network-dependency approach.
- **Shell-based detection only (no extraction)** — Simpler but defeats the purpose of zero-config tool availability. Rejected because the goal is to make native tools available without user intervention.
- **Single jar with all platform binaries** — Would bloat the jar with binaries for every platform. Rejected in favor of platform-specific jars via Maven classifiers.

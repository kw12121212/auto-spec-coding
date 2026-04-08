# Design: builtin-tool-manager

## Approach

Introduce a `BuiltinToolManager` that acts as a resolver for external tool binaries. The manager does not execute tools — it only ensures the binary is available on disk and returns its path.

1. **BuiltinTool enum** — Each supported external tool is an enum constant with metadata: tool name, GitHub owner/repo, binary name patterns per platform, and version tag.

2. **Platform detection** — Use `System.getProperty("os.name")` and `os.arch` to determine the current platform tuple (os, arch). Map to a release artifact filename pattern (e.g., `ripgrep-{version}-x86_64-unknown-linux-musl.tar.gz`).

3. **Resolution flow** — `resolve(BuiltinTool)`:
   - Check local cache directory for an existing binary of the correct version
   - If found, return its path
   - If not found, check system PATH via `which`/`where`
   - If found on PATH, return its path
   - If not found, download from GitHub Releases API, extract, verify checksum, install to cache
   - Return the installed path

4. **Download** — Fetch release info from `https://api.github.com/repos/{owner}/{repo}/releases/latest`, find the matching asset by platform, download to temp file, extract (tar.gz / zip), move to cache.

5. **Checksum** — Verify against `.sha256` or `SHA256SUMS` file from the release assets. If no checksum file is available, skip verification (log a warning).

## Key Decisions

- **Manager does not execute tools** — It only resolves binary paths. Tool implementations (GrepTool, etc.) decide whether to use the native binary or fall back to pure Java.
- **Cache directory default** — `~/.specdriven/bin/` keeps tool binaries in a predictable, user-local location. Overridable via config or system property.
- **No background downloads** — Downloads happen synchronously on `resolve()`. Background download is out of scope for this change.
- **GitHub Releases as sole source** — All supported tools are distributed via GitHub Releases. No custom mirror or CDN support in this change.
- **Version pinning via enum** — Each `BuiltinTool` constant carries a version string. Upgrading means updating the constant. This avoids runtime version management complexity.

## Alternatives Considered

- **Shell-based detection only (no download)** — Simpler but defeats the purpose of zero-config tool availability. Rejected because the goal is to make native tools available without user intervention.
- **System package manager integration** — Would require platform-specific package manager logic (apt, brew, etc.). Rejected because GitHub Releases provides universal cross-platform binaries.
- **Central plugin registry** — A generic plugin system that could install any tool. Rejected as over-engineering for the current three-tool scope. Can be evolved into one later if needed.

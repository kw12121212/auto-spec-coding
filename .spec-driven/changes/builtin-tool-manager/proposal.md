# builtin-tool-manager

## What

Implement a builtin tool manager that automatically detects, downloads, and installs precompiled external tool binaries (ripgrep, gh, fd). The manager provides a unified API for discovering whether an external tool is available on the system PATH or in the local binary cache, and for downloading missing tools from GitHub Releases.

## Why

M2 tool-surface (GrepTool, GlobTool) currently uses pure Java implementations. Native tools like ripgrep and fd offer significantly better performance. Without a builtin tool manager, users must manually install these tools. The manager automates this: detects existing installations, downloads missing binaries to a local cache, and provides resolved paths that tools can use as optional accelerators.

This is the foundational infrastructure for M3 — both `tool-gh` and `tool-fd` planned changes depend on it.

## Scope

- `BuiltinTool` enum describing supported external tools (name, GitHub repo, binary name patterns)
- `BuiltinToolManager` interface and implementation for tool resolution lifecycle:
  - detect: check if tool exists on PATH or in local binary cache
  - resolve: return the absolute path to the tool binary (detect + download if missing)
  - download: fetch precompiled binary from GitHub Releases to local cache
- Platform detection (OS + architecture) for selecting the correct release artifact
- Local binary cache directory management (default `~/.specdriven/bin/`, configurable)
- HTTP download using Lealone async network module
- SHA-256 checksum verification of downloaded binaries

## Unchanged Behavior

- Existing GrepTool and GlobTool pure Java implementations remain unchanged — the builtin tool manager is an optional enhancement layer
- Tool interface, ToolContext, ToolInput, ToolResult are not modified
- Permission model is not affected (the manager itself does not execute tools)
- Config loader is not modified

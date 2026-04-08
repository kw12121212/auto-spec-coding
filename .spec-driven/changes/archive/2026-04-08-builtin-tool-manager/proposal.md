# builtin-tool-manager

## What

Implement a builtin tool manager that extracts precompiled external tool binaries (ripgrep, fd) from classpath resources. The manager provides a unified API for discovering whether an external tool is available on the system PATH or in the local binary cache, and for extracting bundled binaries when they are not found.

## Why

M2 tool-surface (GrepTool, GlobTool) currently uses pure Java implementations. Native tools like ripgrep and fd offer significantly better performance. Without a builtin tool manager, users must manually install these tools. The manager automates this: detects existing installations, extracts bundled binaries to a local cache, and provides resolved paths that tools can use as optional accelerators.

This is the foundational infrastructure for M3 — the `tool-fd` planned change depends on it.

## Scope

- `BuiltinTool` enum describing supported external tools (name, binary name, version tag, resource path)
- `BuiltinToolManager` interface and implementation for tool resolution lifecycle:
  - detect: check if tool exists on PATH or in local binary cache
  - resolve: return the absolute path to the tool binary (detect + extract if missing)
  - extract: copy binary from classpath resources to local cache
- Platform detection (OS + architecture) for selecting the correct resource
- Local binary cache directory management (default `~/.specdriven/bin/`, configurable)
- Platform-specific packaging via Maven classifiers

## Unchanged Behavior

- Existing GrepTool and GlobTool pure Java implementations remain unchanged — the builtin tool manager is an optional enhancement layer
- Tool interface, ToolContext, ToolInput, ToolResult are not modified
- Permission model is not affected (the manager itself does not execute tools)
- Config loader is not modified

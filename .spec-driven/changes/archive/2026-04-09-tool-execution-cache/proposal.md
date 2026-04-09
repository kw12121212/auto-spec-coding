# tool-execution-cache

## What

Add a Lealone DB-backed execution cache for read-only tool results (ReadTool, GrepTool, GlobTool). When a cacheable tool is invoked with the same parameters within its TTL window, the cached result is returned without re-executing the tool. Expired entries are cleaned up by a background thread.

## Why

During a typical agent loop, the same files and searches are read repeatedly across turns. Each redundant invocation wastes I/O and compute. Caching read-only tool results reduces this overhead, especially for large codebases where GrepTool/GlobTool traversals are expensive.

This is the remaining planned change in M17 — `llm-response-cache` already established the Lealone cache pattern.

## Scope

- `ToolCache` interface: generic key-value cache with TTL, hit-count tracking
- `LealoneToolCache` implementation backed by a `tool_cache` table
- `CachingTool` decorator: wraps a `Tool` instance, checks cache before delegating
- Tool-specific TTL defaults (ReadTool: 30s, GrepTool: 60s, GlobTool: 60s)
- Cache key generation from tool name + sorted parameters (SHA-256)
- `TOOL_CACHE_HIT` and `TOOL_CACHE_MISS` event types on `EventBus`
- Background VirtualThread cleanup of expired entries
- File-change invalidation: ReadTool cache entries are invalidated when the source file's last-modified timestamp changes
- Only read-only tools are cached (ReadTool, GrepTool, GlobTool); write/edit tools are never cached

## Unchanged Behavior

- Tools that are not wrapped with `CachingTool` execute exactly as before
- Cache storage failures fall back to direct tool execution (no error propagation)
- Tool permission checks still run before cache lookup
- Existing LLM cache (`LlmCache` / `LealoneLlmCache`) is unaffected

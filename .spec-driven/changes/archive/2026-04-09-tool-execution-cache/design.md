# Design: tool-execution-cache

## Approach

Mirror the proven `LlmCache` / `LealoneLlmCache` / `CachingLlmClient` pattern from the `llm-response-cache` change:

1. **`ToolCache` interface** — generic cache contract with `get(key)`, `put(key, value, ttlMs)`, `invalidate(key)`, `clear()`, independent of `LlmCache`
2. **`LealoneToolCache`** — Lealone-backed implementation with auto-created `tool_cache` table, background cleanup, and `EventBus` integration
3. **`CachingTool` decorator** — implements `Tool` by wrapping a delegate; on `execute()` it generates a cache key from `toolName + parameters`, checks the cache, and either returns the cached result or delegates and caches the response

Cache key = SHA-256 hex of `toolName` + sorted parameter key-value pairs. This matches the deterministic key generation approach used by `CacheKeyGenerator` for LLM requests.

For ReadTool specifically, after a cache hit the decorator checks the file's `lastModified` against the stored timestamp. If the file changed, the entry is invalidated and the tool re-executes.

## Key Decisions

- **Separate `ToolCache` interface from `LlmCache`**: different schemas, different TTL defaults, different invalidation triggers (file-change detection only applies to tools). Avoids coupling.
- **Decorator pattern (`CachingTool`) over tool-internal caching**: keeps cache logic out of individual tool implementations; any tool can be made cacheable by wrapping it.
- **Only cache `ToolResult.Success`**: error results are never cached since the underlying condition may change.
- **TTL defaults by tool type**: ReadTool (30s), GrepTool (60s), GlobTool (60s). Configurable via `CachingTool` constructor.
- **File-change invalidation for ReadTool only**: store `lastModified` in the cache entry metadata; on hit, re-stat the file. GrepTool/GlobTool results depend on directory trees — change detection would be too expensive.

## Alternatives Considered

- **Shared cache table with LLM cache**: rejected — different columns, different cleanup cadences, different invalidation logic. Separation keeps both simple.
- **AOP/proxy-based caching**: rejected — adds complexity beyond what a simple decorator provides; no framework support in the current codebase.
- **Directory-watcher-based invalidation for GrepTool/GlobTool**: rejected — OS file watchers are unreliable across platforms; TTL expiry is sufficient.

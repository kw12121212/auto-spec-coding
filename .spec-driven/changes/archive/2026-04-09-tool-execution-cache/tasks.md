# Tasks: tool-execution-cache

## Implementation

- [x] Add `TOOL_CACHE_HIT` and `TOOL_CACHE_MISS` to `EventType` enum
- [x] Create `ToolCache` interface in `org.specdriven.agent.tool.cache` with methods: `get(key)`, `put(key, output, ttlMs)`, `invalidate(key)`, `clear()`
- [x] Create `ToolCacheKey` utility that generates a deterministic SHA-256 hex key from tool name + sorted parameter map
- [x] Create `LealoneToolCache` implementing `ToolCache` backed by `tool_cache` table (columns: `cache_key VARCHAR(64) PK`, `tool_name VARCHAR`, `output CLOB`, `file_path VARCHAR`, `file_modified BIGINT`, `created_at BIGINT`, `ttl_ms BIGINT`, `hit_count BIGINT`)
- [x] Implement background VirtualThread cleanup in `LealoneToolCache` (hourly sweep of expired entries)
- [x] Implement `TOOL_CACHE_HIT` / `TOOL_CACHE_MISS` event publishing in `LealoneToolCache`
- [x] Create `CachingTool` decorator implementing `Tool` — wraps a delegate `Tool`, checks `ToolCache` before executing
- [x] Implement cache key generation in `CachingTool.execute()` using `ToolCacheKey.generate(getName(), input.parameters())`
- [x] Implement ReadTool file-change invalidation: on cache hit, check `file_path` + `file_modified` columns against current file stat; invalidate if changed
- [x] Only cache `ToolResult.Success` — never cache errors
- [x] Handle cache storage/lookup failures gracefully: log warning and fall back to direct tool execution

## Testing

- [x] Run `mvn compile` — lint/validation: verify all new code compiles
- [x] Run `mvn test -pl . -Dtest="org.specdriven.agent.tool.cache.*"` for unit tests
- [x] Create `ToolCacheKeyTest`: verify same parameters produce same key, different parameters produce different key, key is 64-char hex
- [x] Create `LealoneToolCacheTest`: verify put/get, TTL expiry, manual invalidation, clear, hit-count increment, background cleanup, event publishing
- [x] Create `CachingToolTest`: verify cache hit skips delegate, cache miss calls delegate and caches result, error results not cached, ReadTool file-change invalidation, graceful fallback on cache failure

## Verification

- [x] Verify all new types are in `org.specdriven.agent.tool.cache` package
- [x] Verify `TOOL_CACHE_HIT` and `TOOL_CACHE_MISS` events are published correctly
- [x] Verify cache does not interfere with tools that are not wrapped
- [x] Verify ReadTool file-change detection invalidates stale entries
- [x] Run full test suite: `mvn test`

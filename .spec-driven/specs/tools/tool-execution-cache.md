---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/event/EventType.java
    - src/main/java/org/specdriven/agent/tool/cache/CachingTool.java
    - src/main/java/org/specdriven/agent/tool/cache/LealoneToolCache.java
    - src/main/java/org/specdriven/agent/tool/cache/ToolCache.java
    - src/main/java/org/specdriven/agent/tool/cache/ToolCacheKey.java
  tests:
    - src/test/java/org/specdriven/agent/tool/cache/CachingToolTest.java
    - src/test/java/org/specdriven/agent/tool/cache/LealoneToolCacheTest.java
    - src/test/java/org/specdriven/agent/tool/cache/ToolCacheKeyTest.java
---

# Tool Execution Cache Spec

## ADDED Requirements

### Requirement: ToolCache interface

The system MUST define a `ToolCache` interface in `org.specdriven.agent.tool.cache` for tool execution result caching.

The interface MUST include a nested `CacheEntry` record with fields: `output` (String), `filePath` (String nullable), `fileModified` (long).

The `get(key)` method MUST return `Optional<CacheEntry>` (not just `Optional<String>`) to allow callers to inspect file metadata for staleness checks.

The `put(key, output, ttlMs, filePath, fileModified)` method MUST accept file metadata alongside the output and TTL.

#### Scenario: Cache miss
- GIVEN an empty `ToolCache`
- WHEN `get(key)` is called with any key
- THEN it MUST return `Optional.empty()`

#### Scenario: Cache put and get
- GIVEN a `ToolCache` instance
- WHEN `put(key, output, ttlMs)` is called and then `get(key)` is called with the same key within the TTL
- THEN it MUST return `Optional.of(output)`

#### Scenario: Cache expiry
- GIVEN a `ToolCache` instance with an entry stored with TTL of 1ms
- WHEN `get(key)` is called after the TTL has elapsed
- THEN it MUST return `Optional.empty()`

#### Scenario: Manual invalidation
- GIVEN a `ToolCache` instance with a cached entry under key "k1"
- WHEN `invalidate("k1")` is called
- THEN subsequent `get("k1")` MUST return `Optional.empty()`

#### Scenario: Clear all entries
- GIVEN a `ToolCache` instance with multiple cached entries
- WHEN `clear()` is called
- THEN all subsequent `get()` calls MUST return `Optional.empty()`

### Requirement: ToolCacheKey generation

The system MUST provide a `ToolCacheKey` utility that produces a deterministic cache key from a tool name and parameter map.

#### Scenario: Same inputs produce same key
- GIVEN identical tool name and parameter maps
- WHEN `ToolCacheKey.generate(name, params)` is called on each
- THEN the resulting keys MUST be equal

#### Scenario: Different inputs produce different key
- GIVEN different tool names or parameter maps
- WHEN `ToolCacheKey.generate(name, params)` is called on each
- THEN the resulting keys MUST NOT be equal

#### Scenario: Key is SHA-256 hex string
- GIVEN any tool name and parameter map
- WHEN `ToolCacheKey.generate(name, params)` is called
- THEN the result MUST be a 64-character lowercase hex string

#### Scenario: Parameter order independence
- GIVEN two parameter maps `{"a": "1", "b": "2"}` and `{"b": "2", "a": "1"}`
- WHEN `ToolCacheKey.generate` is called with each
- THEN the resulting keys MUST be equal

### Requirement: LealoneToolCache implementation

The system MUST provide a `LealoneToolCache` implementing `ToolCache` backed by Lealone embedded database.

#### Scenario: Auto-create table on init
- GIVEN a `LealoneToolCache` constructed with a valid JDBC URL
- WHEN construction completes
- THEN a `tool_cache` table MUST exist in the database

#### Scenario: Cache table schema
- GIVEN the `tool_cache` table
- THEN it MUST have columns: `cache_key` (VARCHAR PRIMARY KEY), `tool_name` (VARCHAR), `output` (CLOB), `file_path` (VARCHAR nullable), `file_modified` (BIGINT nullable), `created_at` (BIGINT), `ttl_ms` (BIGINT), `hit_count` (BIGINT)

#### Scenario: Hit count increment
- GIVEN a cached entry with `hit_count` = 0
- WHEN `get(key)` returns a cache hit
- THEN the entry's `hit_count` MUST be incremented by 1

#### Scenario: Background cleanup
- GIVEN a `LealoneToolCache` instance
- WHEN expired entries exist (`created_at + ttl_ms < now()`)
- THEN a background VirtualThread MUST periodically delete them (at least once per hour)
- AND cleanup failures MUST be logged as warnings and MUST NOT propagate exceptions

#### Scenario: Constructor parameters
- GIVEN `LealoneToolCache` is constructed
- THEN it MUST accept `EventBus` and `String jdbcUrl` as constructor parameters

### Requirement: CachingTool decorator

The system MUST provide a `CachingTool` implementing `Tool` that transparently adds caching to any delegate `Tool`.

#### Scenario: Cache hit skips delegate
- GIVEN a `CachingTool` wrapping a delegate `Tool`
- AND the cache already contains a result for the given tool name + parameters
- WHEN `execute(input, context)` is called
- THEN the delegate's `execute()` MUST NOT be called
- AND the cached `ToolResult.Success` MUST be returned

#### Scenario: Cache miss calls delegate
- GIVEN a `CachingTool` wrapping a delegate `Tool`
- AND the cache does not contain a result for the given tool name + parameters
- WHEN `execute(input, context)` is called
- THEN the delegate's `execute()` MUST be called
- AND the result MUST be cached if it is `ToolResult.Success`

#### Scenario: Error results not cached
- GIVEN a `CachingTool` wrapping a delegate `Tool`
- AND the delegate returns `ToolResult.Error`
- WHEN `execute(input, context)` is called
- THEN the error MUST be returned to the caller
- AND the error MUST NOT be stored in the cache

#### Scenario: Constructor parameters
- GIVEN `CachingTool` is constructed
- THEN it MUST accept a `Tool` delegate, a `ToolCache` instance, and a `long ttlMs` parameter

### Requirement: ReadTool file-change invalidation

When a cache hit occurs for a tool named "read", the system MUST validate file freshness.

#### Scenario: File unchanged returns cached result
- GIVEN a cached ReadTool result with `file_path` = "/foo.txt" and `file_modified` = 1000
- AND the file `/foo.txt` currently has `lastModified` = 1000
- WHEN a cache hit occurs
- THEN the cached result MUST be returned

#### Scenario: File changed invalidates and re-executes
- GIVEN a cached ReadTool result with `file_path` = "/foo.txt" and `file_modified` = 1000
- AND the file `/foo.txt` currently has `lastModified` = 2000
- WHEN a cache hit occurs
- THEN the cached entry MUST be invalidated
- AND the delegate tool MUST be re-executed
- AND the new result MUST be cached

#### Scenario: File deleted invalidates
- GIVEN a cached ReadTool result with `file_path` = "/foo.txt"
- AND the file `/foo.txt` no longer exists
- WHEN a cache hit occurs
- THEN the cached entry MUST be invalidated
- AND the delegate tool MUST be re-executed (returning an error)

### Requirement: Cache event publishing

The system MUST publish events on the `EventBus` for tool cache operations.

#### Scenario: Cache hit event
- GIVEN a `LealoneToolCache` with an `EventBus`
- WHEN a cache hit occurs
- THEN an event with type `TOOL_CACHE_HIT` MUST be published

#### Scenario: Cache miss event
- GIVEN a `LealoneToolCache` with an `EventBus`
- WHEN a cache miss occurs
- THEN an event with type `TOOL_CACHE_MISS` MUST be published

### Requirement: Graceful cache failure handling

Cache storage or lookup failures MUST NOT propagate to tool callers.

#### Scenario: Cache put failure falls back silently
- GIVEN a `CachingTool` whose `ToolCache.put()` throws an exception
- WHEN the delegate tool succeeds
- THEN the tool result MUST still be returned to the caller
- AND the exception MUST be logged as a warning

#### Scenario: Cache get failure falls back to delegate
- GIVEN a `CachingTool` whose `ToolCache.get()` throws an exception
- WHEN `execute(input, context)` is called
- THEN the delegate tool MUST be executed
- AND the exception MUST be logged as a warning

# LLM Cache Spec

## ADDED Requirements

### Requirement: LlmCache interface

The system MUST define a `LlmCache` interface in `org.specdriven.agent.llm` for LLM response caching and token usage persistence.

#### Scenario: Cache miss
- GIVEN an empty `LlmCache`
- WHEN `get(key)` is called with any key
- THEN it MUST return `Optional.empty()`

#### Scenario: Cache put and get
- GIVEN an `LlmCache` instance
- WHEN `put(key, responseJson, ttlMs)` is called and then `get(key)` is called with the same key within the TTL
- THEN it MUST return `Optional.of(responseJson)`

#### Scenario: Cache expiry
- GIVEN an `LlmCache` instance with an entry stored with TTL of 1ms
- WHEN `get(key)` is called after the TTL has elapsed
- THEN it MUST return `Optional.empty()`

#### Scenario: Manual invalidation
- GIVEN an `LlmCache` instance with a cached entry under key "k1"
- WHEN `invalidate("k1")` is called
- THEN subsequent `get("k1")` MUST return `Optional.empty()`

#### Scenario: Clear all entries
- GIVEN an `LlmCache` instance with multiple cached entries
- WHEN `clear()` is called
- THEN all subsequent `get()` calls MUST return `Optional.empty()`

### Requirement: Token usage recording

The system MUST persist token usage per LLM call.

#### Scenario: Record usage
- GIVEN an `LlmCache` instance
- WHEN `recordUsage(sessionId, agentName, model, usage)` is called with valid parameters
- THEN the usage MUST be persisted and queryable via `queryUsage()`

#### Scenario: Query usage by session
- GIVEN usage records for sessions "s1" and "s2"
- WHEN `queryUsage("s1", null, 0, Long.MAX_VALUE)` is called
- THEN it MUST return only records matching session "s1"

#### Scenario: Query usage by agent
- GIVEN usage records for agents "a1" and "a2"
- WHEN `queryUsage(null, "a1", 0, Long.MAX_VALUE)` is called
- THEN it MUST return only records matching agent "a1"

#### Scenario: Query usage by time range
- GIVEN usage records at timestamps 1000, 2000, and 3000
- WHEN `queryUsage(null, null, 1500, 2500)` is called
- THEN it MUST return only records with `created_at` in the inclusive range [1500, 2500]

#### Scenario: Query with no matches
- GIVEN an `LlmCache` with usage records
- WHEN `queryUsage()` is called with filters matching nothing
- THEN it MUST return an empty list (not null)

### Requirement: CacheKeyGenerator

The system MUST provide a `CacheKeyGenerator` utility that produces a deterministic cache key from an `LlmRequest`.

#### Scenario: Same request produces same key
- GIVEN two `LlmRequest` instances with identical systemPrompt, messages, tools, temperature, and maxTokens
- WHEN `CacheKeyGenerator.generate(request)` is called on each
- THEN the resulting keys MUST be equal

#### Scenario: Different request produces different key
- GIVEN two `LlmRequest` instances with different messages
- WHEN `CacheKeyGenerator.generate(request)` is called on each
- THEN the resulting keys MUST NOT be equal

#### Scenario: Key is SHA-256 hex string
- GIVEN any `LlmRequest`
- WHEN `CacheKeyGenerator.generate(request)` is called
- THEN the result MUST be a 64-character lowercase hex string
- AND the key is derived from systemPrompt, messages, tools, temperature, and maxTokens

### Requirement: LealoneLlmCache implementation

The system MUST provide a `LealoneLlmCache` implementing `LlmCache` backed by Lealone embedded database.

#### Scenario: Auto-create tables on init
- GIVEN a `LealoneLlmCache` constructed with a valid JDBC URL
- WHEN construction completes
- THEN `llm_cache` and `llm_usage` tables MUST exist in the database

#### Scenario: Cache table schema
- GIVEN the `llm_cache` table
- THEN it MUST have columns: `cache_key` (VARCHAR PRIMARY KEY), `response_json` (CLOB), `created_at` (BIGINT), `ttl_ms` (BIGINT), `hit_count` (BIGINT)

#### Scenario: Usage table schema
- GIVEN the `llm_usage` table
- THEN it MUST have columns: `id` (BIGINT PRIMARY KEY AUTO_INCREMENT), `session_id` (VARCHAR), `agent_name` (VARCHAR), `model` (VARCHAR), `prompt_tokens` (INT), `completion_tokens` (INT), `total_tokens` (INT), `created_at` (BIGINT)

#### Scenario: Hit count increment
- GIVEN a cached entry with `hit_count` = 0
- WHEN `get(key)` returns a cache hit
- THEN the entry's `hit_count` MUST be incremented by 1

#### Scenario: Background cleanup
- GIVEN a `LealoneLlmCache` instance
- WHEN expired entries exist (`created_at + ttl_ms < now()`)
- THEN a background VirtualThread MUST periodically delete them (at least once per hour)
- AND cleanup failures MUST be logged as warnings and MUST NOT propagate exceptions

#### Scenario: Constructor parameters
- GIVEN `LealoneLlmCache` is constructed
- THEN it MUST accept `EventBus` and `String jdbcUrl` as constructor parameters

### Requirement: CachingLlmClient decorator

The system MUST provide a `CachingLlmClient` implementing `LlmClient` that transparently adds caching.

#### Scenario: Cache hit skips delegate
- GIVEN a `CachingLlmClient` wrapping a delegate `LlmClient`
- AND the cache already contains a response for the given request
- WHEN `chat(request)` is called
- THEN the delegate's `chat()` MUST NOT be called
- AND the cached response MUST be returned

#### Scenario: Cache miss calls delegate
- GIVEN a `CachingLlmClient` wrapping a delegate `LlmClient`
- AND the cache does not contain a response for the given request
- WHEN `chat(request)` is called
- THEN the delegate's `chat()` MUST be called
- AND the response MUST be cached
- AND token usage MUST be recorded

#### Scenario: Streaming does not cache responses
- GIVEN a `CachingLlmClient` wrapping a delegate `LlmClient`
- WHEN `chatStreaming(request, callback)` is called
- THEN the delegate's `chatStreaming()` MUST be called
- AND the response MUST NOT be cached
- AND token usage from the streaming response MUST still be recorded via `callback.onComplete()`

#### Scenario: Constructor parameters
- GIVEN `CachingLlmClient` is constructed
- THEN it MUST accept a `LlmClient` delegate, an `LlmCache` instance, and optional `sessionId` and `agentName` strings

### Requirement: Cache event publishing

The system MUST publish events on the `EventBus` for cache operations.

#### Scenario: Cache hit event
- GIVEN a `LealoneLlmCache` with an `EventBus`
- WHEN a cache hit occurs
- THEN an event with type `LLM_CACHE_HIT` MUST be published

#### Scenario: Cache miss event
- GIVEN a `LealoneLlmCache` with an `EventBus`
- WHEN a cache miss occurs
- THEN an event with type `LLM_CACHE_MISS` MUST be published

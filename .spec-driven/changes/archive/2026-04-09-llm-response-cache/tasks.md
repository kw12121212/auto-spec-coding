# Tasks: llm-response-cache

## Implementation

- [x] 定义 `LlmCache` 接口（`org.specdriven.agent.llm`）：`get(String key)`, `put(String key, String responseJson, long ttlMs)`, `invalidate(String key)`, `recordUsage(String sessionId, String agentName, String model, LlmUsage usage)`, `queryUsage(String sessionId, String agentName, long from, long to)`, `clear()`
- [x] 实现 `CacheKeyGenerator` 工具类：对 `LlmRequest` 的 model + messages JSON + tools JSON + temperature 做 SHA-256 哈希，返回 hex string key
- [x] 实现 `LealoneLlmCache`：创建 `llm_cache` 表（key, response_json, created_at, ttl_ms, hit_count）和 `llm_usage` 表（id, session_id, agent_name, model, prompt_tokens, completion_tokens, total_tokens, created_at）；实现 `get/put/invalidate/recordUsage/queryUsage` 方法
- [x] 实现 `LealoneLlmCache` 后台清理线程：VirtualThread 每小时删除过期缓存条目，失败只 log warning 不抛异常
- [x] 实现 `CachingLlmClient` 装饰器：实现 `LlmClient` 接口，`chat()` 先查缓存→命中则返回→未命中则调用 delegate→写入缓存→记录 usage
- [x] 实现 `CachingLlmClient` 的 `chatStreaming()` 方法：streaming 请求不查缓存，但仍然记录 token usage

## Testing

- [x] `mvn compile` — lint and validation
- [x] `mvn test` — unit tests: LealoneLlmCacheTest, CacheKeyGeneratorTest, CachingLlmClientTest
- [x] `LealoneLlmCacheTest` — 测试缓存命中（相同 request 返回缓存响应）、缓存失效（TTL 过期后返回 empty）、手动 invalidate、并发读写安全性
- [x] `LealoneLlmCacheTest` — 测试 usage 记录：单条记录、按 session 聚合、按 agent 聚合、按时间范围过滤、空结果返回空列表
- [x] `CacheKeyGeneratorTest` — 测试相同输入产生相同 key、不同输入产生不同 key、null/empty 工具列表的 key 稳定性
- [x] `CachingLlmClientTest` — 测试缓存命中跳过 delegate 调用、缓存未命中调用 delegate 并写入缓存、streaming 不缓存但记录 usage

## Verification

- [x] 所有单元测试通过：`mvn test -pl . -q`
- [x] 缓存命中时不产生 HTTP 请求到 LLM API
- [x] 未启用缓存时（直接使用原始 LlmClient）行为完全不变
- [x] Lealone 数据库表在首次使用时自动创建，无需手动 DDL

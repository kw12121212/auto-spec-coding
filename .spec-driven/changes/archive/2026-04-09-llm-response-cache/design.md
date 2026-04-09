# Design: llm-response-cache

## Approach

采用装饰器模式在 `LlmClient` 外层透明添加缓存能力，不修改现有 provider 实现。

1. **LlmCache 接口** — 定义缓存操作的抽象契约：`get(key)`, `put(key, response, ttl)`, `invalidate(key)`, 以及用量记录 `recordUsage(session, agent, usage)`
2. **Cache key 生成** — 对 `LlmRequest` 的 `model` + 序列化 `messages` + 序列化 `tools` + `temperature` 拼接后做 SHA-256 哈希，生成固定长度 key。精确匹配，不做语义近似
3. **LealoneLlmCache** — 两张表：
   - `llm_cache`：存储 cache key、序列化 response JSON、创建时间、TTL、命中次数
   - `llm_usage`：存储 session ID、agent name、model、prompt/completion/total tokens、时间戳
4. **CachingLlmClient** — 实现 `LlmClient` 接口，内部委托给实际 client。`chat()` 调用前先查缓存，命中则直接返回；未命中则调用真实 client、写入缓存、记录 usage
5. **后台清理** — VirtualThread 每小时扫描 `llm_cache` 删除 `created_at + ttl_ms < now()` 的过期条目，与现有 store 的 cleanup 模式一致

## Key Decisions

- **装饰器而非代理**：`CachingLlmClient` 包装 `LlmClient`，不修改也不继承任何 provider 实现。调用方通过组合方式启用缓存
- **精确匹配缓存 key**：M17 out-of-scope 明确排除了语义匹配，首期只做 request 级别精确匹配。通过确定性序列化保证相同输入产生相同 key
- **独立 usage 表**：token 用量与缓存条目分开存储，因为用量需要在缓存失效后仍然保留用于成本分析
- **TTL 而非 LRU**：Lealone 嵌入式数据库没有内置 LRU，使用时间过期策略更简单可靠
- **默认 TTL 5 分钟**：LLM 响应缓存时效性要求适中，5 分钟平衡了命中率和数据新鲜度

## Alternatives Considered

- **内存 HashMap 缓存**：实现简单但进程重启丢失，无法持久化 usage 数据。不适合需要跨 session 追踪 token 用量的场景
- **在 Provider 层内置缓存**：侵入性强，需修改每个 provider 实现。装饰器模式更解耦
- **LRU 淘汰策略**：需要维护访问顺序和容量限制，增加复杂度。TTL 策略对于带时间戳的响应缓存更自然
- **单一混合表**：将缓存和用量合并为一张表会导致查询效率低下（缓存需要 key 查询，用量需要范围聚合），分离更清晰

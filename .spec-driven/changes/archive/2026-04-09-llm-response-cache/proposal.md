# llm-response-cache

## What

实现基于 Lealone 嵌入式数据库的 LLM 响应缓存层：对相同 prompt 输入的 `LlmRequest` 进行精确匹配缓存，命中时直接返回已缓存的 `LlmResponse` 跳过 API 调用；同时将每次 LLM 调用的 token 用量持久化到数据库，支持按 session、agent、时间维度聚合查询。

## Why

- **降低 API 成本**：重复或相似的请求（如重试、模板化调用）可命中缓存，避免重复 token 消耗
- **减少延迟**：缓存命中时省去网络往返，提升响应速度
- **用量可观测**：token 使用量持久化后可按维度聚合，支持成本追踪和预算控制
- **M17 里程碑首个变更**：为后续 `tool-execution-cache` 奠定缓存基础设施

## Scope

**In Scope:**
- `LlmCache` 接口定义（查询、写入、失效、用量记录）
- `LealoneLlmCache` 实现（基于 JDBC 的 Lealone 存储）
- 缓存 key 生成（对 LlmRequest 的 model + messages + tools + temperature 做 SHA-256 哈希）
- TTL 过期自动失效（默认 5 分钟）
- Token usage 持久化（按 session/agent 聚合表）
- Token usage 聚合查询（按 session、agent、时间范围）
- `CachingLlmClient` 装饰器（在 LlmClient 外层透明添加缓存）
- 后台 VirtualThread 定期清理过期条目
- 单元测试覆盖缓存命中、失效、并发、用量聚合场景

**Out of Scope:**
- 语义级 prompt 相似度匹配（首期仅精确匹配）
- Tool 执行结果缓存（属于 `tool-execution-cache` 变更）
- 缓存预热策略
- 分布式缓存（单进程嵌入模式）
- JSON-RPC / HTTP 层的缓存 API 暴露

## Unchanged Behavior

- `LlmClient` 和 `LlmProvider` 接口不变
- `LlmRequest` / `LlmResponse` 数据结构不变
- 未启用缓存时（无 `CachingLlmClient` 包装），所有调用行为与现在完全一致
- 现有 provider 实现（OpenAiClient、ClaudeClient）不做任何修改

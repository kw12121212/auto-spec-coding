# M17 - Lealone DB 缓存层

## Goal

利用 Lealone 嵌入式数据库为 LLM 调用和 Tool 执行提供统一的缓存基础设施，减少重复计算、IO 和 token 消耗。

## In Scope

- LLM 响应缓存（相同/相似 prompt 命中缓存，避免重复 API 调用）
- Token 用量统计持久化（按 session/agent 聚合，支持成本追踪）
- Tool 执行结果缓存（ReadTool 文件内容、GrepTool/GlobTool 查询结果，带 TTL）
- 缓存失效策略（TTL、手动失效、依赖文件变更检测）

## Out of Scope

- 分布式缓存（单进程嵌入模式）
- 语义级 prompt 相似度匹配（首期仅精确匹配）
- 缓存预热策略

## Done Criteria

- LLM 缓存可在相同 prompt 输入时直接返回缓存响应，跳过 API 调用
- Token 用量可按 session、agent、时间维度查询聚合
- Tool 缓存可对 ReadTool/GrepTool/GlobTool 的结果做 TTL 缓存
- 缓存条目过期后自动失效
- 有单元测试覆盖缓存命中、失效、并发访问场景

## Planned Changes
- `llm-response-cache` - Declared: complete - LLM 响应缓存与 token 用量统计持久化，基于 Lealone DB
- `tool-execution-cache` - Declared: complete - Tool 执行结果缓存（带 TTL 自动失效），基于 Lealone DB

## Dependencies

- M1 核心接口（Tool 接口、Event 接口）
- M2 基础工具集（ReadTool、GrepTool、GlobTool）
- M5 LLM 后端（LLM 调用接口）
- Lealone 数据库模块（lealone-db, lealone-sql）

## Risks

- 缓存命中率取决于 prompt 匹配策略，精确匹配命中率可能较低
- Tool 缓存 TTL 过短则命中率低、过长则结果过时
- 缓存存储空间需管理，避免无限增长

## Status

- Declared: complete

## Notes

- 与 M5、M7 可并行开发；缓存作为中间层插入 LLM provider 和 Tool 实现之间
- TTL 默认值建议按工具类型区分：文件内容缓存较短（秒级），LLM 响应缓存可较长（分钟级）
- 后续可扩展为语义级匹配（embedding 相似度），首期不做


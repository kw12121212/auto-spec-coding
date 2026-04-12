# M28 - 动态 LLM 配置与热切换

## Goal

利用 Lealone `SET LLM` SQL 语法和 `Database.setLLMParameters()` 机制，让本项目的多 provider 架构支持运行时动态配置和热切换非敏感 LLM 参数（model、provider、temperature 等），无需重启服务，并为后续密钥引用治理提供稳定配置骨架。

## In Scope

- 基于不可变配置快照的动态更新接口：支持运行时修改 model、provider、url、temperature、timeout 等非敏感参数
- 基于 Lealone DB 的配置持久化：LLM 配置变更写入数据库，重启后恢复最后有效的非敏感参数
- `SET LLM` SQL 语句对接：通过 Lealone 的 SetDatabase 语句动态下发配置
- Per-session / per-agent 粒度的 LLM 配置隔离
- LlmProviderRegistry 运行时热切换：已注册的 provider 可在不中断会话的情况下更换底层参数
- 配置变更事件通知：通过 EventBus 发布配置变更事件，下游组件可响应

## Out of Scope

- 新增 provider 类型（由 M5 覆盖）
- LLM 调用本身的流式处理（M19）
- 配置的 Web UI 管理界面（可通过 HTTP API 间接操作）
- 跨进程配置同步
- SecretVault / VaultResolver 集成与密钥引用治理（由后续 milestone 覆盖）
- `SET LLM` 的共享环境权限治理与审计（由后续 milestone 覆盖）

## Done Criteria

- 运行时替换新的 LLM 配置快照后，后续 LLM 调用可立即生效，无需调用方手动重建 client 实例
- 通过 `SET LLM MODEL=xxx, PROVIDER=xxx` SQL 语句可动态切换当前 session 的 LLM 配置
- 配置变更 MUST 持久化到 Lealone DB，服务重启后自动恢复最后有效的非敏感配置
- 不同 session 可持有不同的 LLM 配置互不干扰
- 配置变更 MUST 触发 EventType.LLM_CONFIG_CHANGED 事件
- OpenAiClient / ClaudeClient 后续请求必须支持读取更新后的配置快照（特别是 model 和 baseUrl）
- 有单元测试覆盖动态更新、持久化恢复、session 隔离、事件发布、并发安全场景

## Planned Changes
- `dynamic-llm-config-snapshots` - Declared: complete - 引入不可变 LLM 配置快照与原子替换机制，支持运行时更新后续请求使用的新参数
- `llm-config-persistence` - Declared: planned - 基于 Lealone DB 持久化非敏感 LLM 配置，含版本记录与回滚能力
- `set-llm-sql-handler` - Declared: planned - 对接 Lealone SET LLM SQL 语句，解析参数并分发到 DynamicLlmConfig
- `provider-config-refresh` - Declared: planned - 扩展 LlmProviderRegistry 支持运行时切换后续请求所用的 provider 参数快照，保证正在进行的请求不受影响
- `llm-config-events` - Declared: planned - 定义 LLM 配置变更事件模型并通过 EventBus 发布，支持下游消费

## Dependencies

- M5 LLM Provider Layer（LlmProvider、LlmClient、LlmConfig 基础接口）
- M1 核心接口（EventBus 用于事件发布）
- Lealone 嵌入式数据库（用于配置持久化）
- Lealone 更新：`a584523` SET LLM 语句 + `Database.setLLMParameters()` + `CodeAgentBase.init(Map)` 动态初始化

## Risks

- 正在进行的 LLM 请求在配置切换过程中可能出现行为不一致
- 配置频繁变更可能导致 provider 连接池状态混乱
- 在共享环境中若缺少权限治理，错误的 SET LLM 语句可能导致服务不可用
- OpenAI/Claude SDK 的客户端对象可能不支持所有参数的热更新

## Status
- Declared: proposed

## Notes

- 配置变更应采用 Copy-on-Write 策略：创建新配置快照后原子替换引用，避免并发读写问题
- 参考Lealone Database.llmParameters 的 CaseInsensitiveMap<String> 存储方式
- 首期仅支持单连接级别（per-session）配置，暂不做全局广播
- 共享或生产环境中的密钥引用治理、权限控制与审计由后续 milestone 覆盖


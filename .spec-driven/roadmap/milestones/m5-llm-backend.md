# M5 - LLM Provider Layer

## Goal

实现 LLM provider 层：多 provider 注册表、provider-agnostic 客户端抽象，以及 OpenAI/Claude 两种 API 的具体实现。HTTP 客户端使用 Lealone 异步网络模块。

## In Scope

- LLM 客户端抽象接口（provider-agnostic）
- 多 Provider 注册表（命名实例、按请求路由、skill-to-provider 绑定）
- OpenAI API 兼容 provider（覆盖 OpenAI 及 OpenAI 兼容供应商）
- Anthropic Claude Messages API provider
- 请求重试与错误处理

## Out of Scope

- 流式响应处理（M19）
- Token 计数与上下文窗口管理（M19）
- 上下文压缩/截断策略（属于会话管理增强）
- Agent 编排逻辑（M4）
- 非 OpenAI/Claude 格式的 provider（可后续扩展）

## Done Criteria

- LLM 客户端可成功调用 OpenAI API 和 Claude API 并获得完整响应
- OpenAI 兼容供应商可通过配置 base URL 直接使用
- 多个 provider 可同时注册，运行时按名称选择
- skill-routing 配置可按 skill/function 名称绑定特定 provider+model
- 请求失败时有合理的重试和错误上报
- 有单元测试覆盖（使用 mock HTTP server）

## Planned Changes

- `llm-provider-interface` - Declared: complete - Provider-agnostic LLM 客户端抽象接口与统一消息类型定义
- `llm-provider-registry` - Declared: complete - 多 Provider 注册表（命名实例管理、按请求路由、skill-to-provider 绑定、default provider 回退）
- `llm-provider-openai` - Declared: complete - OpenAI API 兼容 provider 实现
- `llm-provider-claude` - Declared: complete - Anthropic Claude Messages API provider 实现

## Dependencies

- M1 核心接口（Event 接口用于事件定义）
- Lealone 异步网络模块（lealone-net）用于 HTTP 调用

## Risks

- OpenAI 和 Claude API 版本升级可能导致适配层变更
- 两种 API 格式的差异（消息结构、tool call 格式）增加抽象层复杂度
- 多 provider 配置的验证和冲突处理需明确策略

## Status

- Declared: complete





## Notes

- OpenAI 格式是行业事实标准，兼容此格式可覆盖 DeepSeek、Moonshot、GLM 等多数供应商
- 两种 provider 共享统一的抽象接口，新增 provider 只需实现 Provider 接口
- 与 M2、M4 可并行开发；M4 使用 mock LLM 完成编排测试，M5 提供真实实现后两者集成
- HTTP 客户端使用 Lealone 的异步网络能力（lealone-net），不引入 Apache HttpClient 等外部依赖
- llm-provider-registry 支持同时注册多个 provider（OpenAI 兼容、Claude 等），每个 provider 有独立名称和配置
- 运行时通过 LlmRequest.providerName() 指定使用哪个 provider，未指定时使用 default provider
- skill-routing 配置允许按 skill/function 名称绑定特定 provider+model，实现不同功能使用不同 LLM
- 配置示例：providers 声明具名实例 + default 指定默认 + skill-routing 映射 skill→provider
- registry 应在 llm-provider-openai/claude 之前或并行开发，为 provider 实现提供注册目标

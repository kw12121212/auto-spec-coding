# M5 - LLM 后端集成

## Goal

实现 LLM 调用层，为 agent 的 think 步骤提供大语言模型推理能力，支持多 provider 和流式响应。HTTP 客户端使用 Lealone 异步网络模块。

## In Scope

- LLM 客户端抽象接口（provider-agnostic）
- OpenAI API 兼容 provider（覆盖 OpenAI 及 OpenAI 兼容供应商）
- Anthropic Claude Messages API provider
- 流式响应处理（SSE 解析，统一两种 API 的 streaming 格式）
- Token 计数与上下文窗口管理
- 请求重试与错误处理

## Out of Scope

- 上下文压缩/截断策略（属于会话管理增强）
- Agent 编排逻辑（M4）
- 非 OpenAI/Claude 格式的 provider（可后续扩展）

## Done Criteria

- LLM 客户端可成功调用 OpenAI API 和 Claude API 并获得完整响应
- OpenAI 兼容供应商可通过配置 base URL 直接使用
- 流式响应可逐 token 回调到调用方
- Token 计数可在发送前估算消息占用
- 请求失败时有合理的重试和错误上报
- 有单元测试覆盖（使用 mock HTTP server）

## Planned Changes

- `llm-provider-interface` - Declared: planned - Provider-agnostic LLM 客户端抽象接口与统一消息类型定义
- `llm-provider-openai` - Declared: planned - OpenAI API 兼容 provider 实现
- `llm-provider-claude` - Declared: planned - Anthropic Claude Messages API provider 实现
- `llm-streaming` - Declared: planned - 统一流式响应处理（SSE 解析 + 回调机制）
- `llm-token-counter` - Declared: planned - Token 计数与上下文窗口管理实现

## Dependencies

- M1 核心接口（Event 接口用于流式事件）
- Lealone 异步网络模块（lealone-net）用于 HTTP 调用

## Risks

- OpenAI 和 Claude API 版本升级可能导致适配层变更
- 两种 API 格式的差异（消息结构、tool call 格式、流式协议）增加抽象层复杂度
- Token 计数的精确性受 tokenizer 实现影响
- 流式 SSE 响应的错误恢复和中断处理复杂度

## Status

- Declared: proposed

## Notes

- OpenAI 格式是行业事实标准，兼容此格式可覆盖 DeepSeek、Moonshot、GLM 等多数供应商
- 两种 provider 共享统一的抽象接口，新增 provider 只需实现 Provider 接口
- 与 M2、M4 可并行开发；M4 使用 mock LLM 完成编排测试，M5 提供真实实现后两者集成
- HTTP 客户端使用 Lealone 的异步网络能力（lealone-net），不引入 Apache HttpClient 等外部依赖

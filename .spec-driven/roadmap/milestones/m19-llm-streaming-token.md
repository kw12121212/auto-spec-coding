# M19 - LLM Streaming & Token Management

## Goal

在 M05 provider 层之上实现统一流式响应处理和 token 计数与上下文窗口管理，为 agent 提供实时 token 回调和发送前用量估算能力。

## In Scope

- 统一流式响应处理（SSE 解析，兼容 OpenAI 和 Claude 两种 streaming 格式）
- Token 计数与上下文窗口管理（发送前估算、窗口边界检测）
- 流式回调到调用方（逐 token 推送）

## Out of Scope

- Provider 实现（M05）
- 上下文压缩/截断策略（属于会话管理增强）
- 缓存（M17）

## Done Criteria

- 流式响应可逐 token 回调到调用方
- OpenAI 和 Claude 两种 SSE 格式统一为相同的回调接口
- Token 计数可在发送前估算消息占用
- 上下文窗口超限时可检测并上报
- 有单元测试覆盖（使用 mock SSE stream）

## Planned Changes
- `llm-streaming` - Declared: complete - 统一流式响应处理（SSE 解析 + 回调机制）与 Token 计数与上下文窗口管理

## Dependencies

- M05 LLM Provider Layer（LlmClient、LlmStreamCallback、LlmUsage 接口）
- M01 核心接口（Event 接口用于流式事件）

## Risks

- 流式 SSE 响应的错误恢复和中断处理复杂度
- Token 计数的精确性受 tokenizer 实现影响
- 两种 API 的 SSE 协议差异（OpenAI `data: [DONE]` vs Claude `event: message_stop`）

## Status

- Declared: complete

## Notes

- 流式处理需兼容两种 SSE 协议差异：OpenAI 用 `data: [DONE]` 结束，Claude 用 `event: message_stop`
- Token 计数首期可使用近似估算（字符数/4），后续可集成 tiktoken 等精确 tokenizer
- 与 M05 的 provider 实现可并行开发，通过 LlmStreamCallback 接口解耦


# llm-provider-interface

## What

定义 provider-agnostic 的 LLM 客户端抽象接口与统一消息类型，增强现有 `LlmClient` 接口以支持工具定义、流式回调和多 provider 配置。这是 M5 LLM 后端集成的第一个 change，为后续 OpenAI/Claude provider 实现和流式处理提供类型基础。

## Why

- 现有 `LlmClient` 接口仅有一个 `chat(List<Message>)` 方法，缺少工具定义传递、流式响应、provider 配置等 LLM 调用的核心能力
- M4 编排循环（`DefaultOrchestrator`）已通过 mock LlmClient 工作，需要真实 LLM 后端才能完成端到端
- M11 skill-executor-plugin 的 agent 循环依赖 LLM 推理，M5 是关键路径阻塞项
- OpenAI 和 Claude API 在消息格式、tool call 结构、流式协议上差异显著，需要统一的抽象层隔离差异

## Scope

- 定义 `LlmProvider` 接口：provider 生命周期（初始化、配置、关闭）
- 增强 `LlmClient`：支持传入工具定义列表（Tool → LLM tool schema 映射）、system prompt、temperature 等参数
- 定义 `LlmRequest` / `LlmRequest.Builder`：统一请求构建，包含 messages、tools、config
- 增强 `LlmResponse`：增加 usage（token 统计）、finishReason 等字段
- 定义 `LlmStreamCallback`：流式响应回调接口（为 M5 llm-streaming 预留）
- 定义 `LlmConfig`：provider 配置（base URL、API key、model name、timeout 等）
- 定义 `ToolSchema`：Tool → LLM tool definition 的映射类型

## Unchanged Behavior

- `Message` sealed interface（UserMessage、AssistantMessage、ToolMessage、SystemMessage）保持不变
- `ToolCall` record 保持不变
- `DefaultOrchestrator` 的循环逻辑保持不变——它调用 `LlmClient.chat()`，此变更只增强该方法的能力
- `Tool`、`ToolInput`、`ToolResult`、`ToolParameter` 等工具接口保持不变

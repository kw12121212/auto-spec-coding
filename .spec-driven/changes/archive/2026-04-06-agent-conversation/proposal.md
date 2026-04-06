# agent-conversation

## What

实现会话与消息管理：定义消息类型（user、assistant、tool、system），实现会话上下文以持久化消息历史，并将会话集成到现有 AgentContext 中。

## Why

M4 的 `agent-lifecycle` 已完成，Agent 具备了状态机和生命周期管理能力，但尚无会话/消息管理机制。后续的 `agent-orchestrator`（多轮工具调用编排）需要一个会话层来跟踪对话上下文和消息历史。此外，M5（LLM 后端）也需要消息结构来构建 LLM 请求。

## Scope

- 定义 `Message` sealed interface 及其子类型（UserMessage、AssistantMessage、ToolMessage、SystemMessage）
- 定义 `Conversation` 类管理消息历史的增删查
- 扩展 `AgentContext` 以提供会话访问能力
- 单元测试验证消息不可变性、会话历史持久性

## Unchanged Behavior

- Agent 生命周期状态机（init/start/stop/close/execute）不变
- AgentState 枚举不变
- AgentContext 现有方法签名不变（sessionId、config、toolRegistry）
- DefaultAgent 状态转换逻辑不变

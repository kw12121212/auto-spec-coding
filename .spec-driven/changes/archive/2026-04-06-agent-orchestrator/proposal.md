# agent-orchestrator

## What

实现 agent 的多轮工具调用编排循环（receive → think → act → observe），使 agent 能在单次 execute 调用中完成多步推理和工具调用，使用 JDK 25 VirtualThread 实现并发控制。

## Why

M4 的 `agent-lifecycle` 和 `agent-conversation` 已完成，Agent 具备了状态机和会话管理能力，但 `execute()` 目前是空操作（`doExecute` no-op）。编排循环是 agent 运行时的核心——它将对话上下文、工具注册表、LLM 推理（M5 提供）串联为完整的自主执行闭环。完成后 M4 基础能力闭合，为 M5（LLM 真实调用）、M6（权限注入）、M7（注册表使用）铺平道路。

## Scope

- 定义 `LlmClient` 接口（provider-agnostic），orchestrator 通过此接口请求 LLM 推理
- 定义 `LlmResponse` sealed interface：`TextResponse`（纯文本）和 `ToolCallResponse`（工具调用请求）
- 定义 `ToolCall` record 表示 LLM 返回的工具调用意图（toolName + parameters）
- 定义 `Orchestrator` 接口和 `DefaultOrchestrator` 实现，包含 receive → think → act → observe 循环
- 定义 `OrchestratorConfig` 支持最大循环轮数、单工具超时等参数
- 覆写 `DefaultAgent.doExecute` 委托给 Orchestrator
- 使用 VirtualThread 并行执行多个 ToolCall
- 单元测试：mock LLM + stub Tool 覆盖完整编排路径

## Unchanged Behavior

- Agent 生命周期状态机（init/start/stop/close/execute）不变
- AgentState 枚举和状态转换规则不变
- Message sealed interface 及子类型不变
- Conversation 类不变
- AgentContext 接口不变
- Tool 接口不变

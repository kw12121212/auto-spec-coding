# agent-lifecycle

## What

实现 Agent 状态机与生命周期管理，包括 `AgentState` 的合法转换规则、`init → start → execute → stop → close` 生命周期方法的具体实现，以及 Lealone plugin lifecycle 模式的集成。

## Why

M1 阶段定义了 `Agent` 接口、`AgentState` 枚举和 `AgentContext`，但尚未提供具体实现。Agent 状态机是 M4 agent 运行时的基础——后续 `agent-conversation`（会话管理）和 `agent-orchestrator`（编排循环）都依赖状态机的正确运转。需要先实现这个基础层。

## Scope

**In scope:**
- AgentState 合法转换规则定义与实现
- DefaultAgent 实现类（实现 Agent 接口的所有生命周期方法）
- 状态转换时的边界检查（非法转换抛出 IllegalStateException）
- Lealone plugin lifecycle 模式集成（init/start/stop/close 方法签名对齐）
- 单元测试覆盖所有合法与非法状态转换

**Out of scope:**
- 会话/消息管理（`agent-conversation` change）
- 多轮工具调用编排循环（`agent-orchestrator` change）
- 真实 LLM 调用（使用 mock/stub 即可）
- pause/resume 语义的具体行为（仅定义 PAUSED 状态的进入和退出转换）

## Unchanged Behavior

- Agent 接口签名不变
- AgentState 枚举值不变（IDLE, RUNNING, PAUSED, STOPPED, ERROR）
- AgentContext 接口不变

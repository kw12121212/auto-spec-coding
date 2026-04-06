# M4 - Agent 生命周期与编排

## Goal

实现 agent 核心运行时：状态机、会话管理和多轮工具调用编排循环。

## In Scope

- Agent 状态机与生命周期管理（初始化、运行、暂停、终止）
- 会话/消息管理（对话上下文、消息历史）
- 多轮工具调用编排循环（receive -> think -> act -> observe）
- 使用 Java VirtualThread 实现并发控制

## Out of Scope

- 具体工具实现（M2、M9、M10）
- LLM 实际调用（M5）
- 权限执行（M6）
- 外部接口暴露（M11-M13）

## Done Criteria

- Agent 可完成一次完整的 think-act-observe 循环（使用 mock LLM 和 stub tool）
- 会话上下文在多轮调用间正确保持
- Agent 状态转换符合生命周期模型定义
- 有单元测试验证核心编排逻辑

## Planned Changes

- `agent-lifecycle` - Declared: planned - Agent 状态机与生命周期管理实现
- `agent-conversation` - Declared: planned - 会话与消息管理实现
- `agent-orchestrator` - Declared: planned - 多轮工具调用编排循环实现（VirtualThread 并发）

## Dependencies

- M1 核心接口（Agent 接口、Tool 接口）

## Risks

- 编排循环的复杂度可能随工具数量增长而快速上升
- 上下文窗口管理策略需要提前考虑（M5 LLM 后端将引入 token 计数）

## Status

- Declared: proposed

## Notes

- Agent 生命周期模型需与 spec-coding-sdk 的 Go 实现保持语义一致
- M2 基础工具集和 M5 LLM 后端为软依赖：M4 测试使用 mock LLM + stub Tool
- 与 M2、M5 可并行开发
- 使用 JDK 25 VirtualThread 替代 Go goroutine 模型

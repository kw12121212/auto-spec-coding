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

- `agent-lifecycle` - Declared: complete - Agent 状态机与生命周期管理实现
- `agent-conversation` - Declared: complete - 会话与消息管理实现
- `agent-orchestrator` - Declared: complete - 多轮工具调用编排循环实现（VirtualThread 并发）
- `agent-session-store` - Declared: complete - Session/Conversation 持久化与 Agent 状态快照，基于 Lealone DB 支持崩溃恢复和跨 session 对话续接
- `event-audit-log` - Declared: complete - EventBus 事件持久化存储，支持审计查询、调试回放和行为链路追踪


- `agent-conversation` - Declared: complete - 会话与消息管理实现
- `agent-orchestrator` - Declared: complete - 多轮工具调用编排循环实现（VirtualThread 并发）
- `agent-session-store` - Declared: complete - Session/Conversation 持久化与 Agent 状态快照，基于 Lealone DB 支持崩溃恢复和跨 session 对话续接
- `event-audit-log` - Declared: complete - EventBus 事件持久化存储，支持审计查询、调试回放和行为链路追踪

## Dependencies

- M1 核心接口（Agent 接口、Tool 接口）
- Lealone 数据库模块（lealone-db, lealone-sql）用于 session 和事件持久化

## Risks

- 编排循环的复杂度可能随工具数量增长而快速上升
- 上下文窗口管理策略需要提前考虑（M5 LLM 后端将引入 token 计数）
- 事件持久化的写入吞吐需与 EventBus 发布频率匹配，高频场景可能需异步批量写入
- Session 持久化的序列化格式需兼容 agent 状态机的版本演进

## Status

- Declared: complete

## Notes

- Agent 生命周期模型需与 spec-coding-sdk 的 Go 实现保持语义一致
- M2 基础工具集和 M5 LLM 后端为软依赖：M4 测试使用 mock LLM + stub Tool
- 与 M2、M5 可并行开发
- 使用 JDK 25 VirtualThread 替代 Go goroutine 模型
- agent-session-store 使 agent 具备可恢复性：崩溃/重启后可从 DB 恢复对话和状态，这对 M14 HTTP REST API 的长连接场景尤为关键
- event-audit-log 可通过 EventBus subscriber 异步写入 DB，不阻塞主事件分发路径

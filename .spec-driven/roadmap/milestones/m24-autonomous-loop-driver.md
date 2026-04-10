# M24 - 内置自主循环驱动

## Goal

实现一个内置的自主循环驱动工具，能够基于已创建好的 roadmap 和 milestone 持续自动推进项目开发：按 `recommend → auto` 流程循环执行每个 change，在 context 窗口耗尽时自动重启上下文，中间遇到的问题由副 AI agent（M22 Answer Agent）自动处理，仅在完全无法解决的问题出现时才升级为人工介入。

## In Scope

- 循环调度器：读取 roadmap/milestone 状态，确定下一个待执行的 change
- Recommend → Auto 执行循环：调用 `roadmap-recommend` 获取推荐 change → 调用 `spec-driven-auto` 自动完成（propose → implement → verify → review → archive）
- Context 生命周期管理：在每个 recommend/auto 周期结束后检测 context 使用量，超阈值时保存状态并启动新 session 继续循环
- 问题自动处理集成：复用 M22 的 Answer Agent 机制，循环中遇到的 question 自动路由给 AI agent 回复
- 人工升级门控：仅当 Answer Agent 标记为无法解决 / 必须人工确认时暂停循环并通知人工
- 循环进度持久化：每次循环结束记录已完成的 change、当前 milestone 进度、失败/跳过原因
- 循环控制接口：支持启动、暂停、恢复、停止循环；支持配置最大循环次数、单次超时等安全限制

## Out of Scope

- Roadmap/Milestone 本身的创建和规划（前置条件，不在本里程碑范围）
- 具体业务代码的实现（由 spec-driven-auto 内部完成）
- 多个循环实例并行运行的资源竞争仲裁
- 分布式多机器协同循环执行

## Done Criteria

- 系统可基于已有 roadmap 启动自主循环，自动按 recommend → auto 流程逐个执行 change
- 每个 recommend/auto 周期完成后系统可检测 context 使用量，超阈值时保存状态并重启 session 继续循环
- 循环中遇到的 question 可自动路由给 Answer Agent 处理，无需人工干预即可恢复执行
- 当问题被标记为必须人工确认或 Answer Agent 无法解决时，循环 MUST 暂停并通知人工
- 循环进度 MUST 持久化存储，支持从断点恢复（崩溃重启后可继续上次的进度）
- 提供启动/暂停/恢复/停止循环的控制接口，支持配置安全限制（最大循环次数、单次超时等）
- 有单元测试覆盖循环调度、context 切换、问题自动处理、人工升级门控、进度持久化和崩溃恢复场景

## Planned Changes

- `loop-driver-core` - Declared: planned - 循环调度核心：读取 roadmap 状态机、选择下一个待执行 change、维护循环状态与迭代计数
- `loop-recommend-auto-pipeline` - Declared: planned - Recommend → Auto 执行流水线：串联调用 roadmap-recommend 和 spec-driven-auto skill，处理每轮结果与错误分类
- `context-lifecycle-manager` - Declared: planned - Context 生命周期管理：追踪 token 用量、阈值判断、session 切换与状态恢复
- `loop-answer-agent-integration` - Declared: planned - 循环中 Question 自动处理：对接 M22 Answer Agent 运行时，自动回复循环中产生的 question 并恢复执行
- `loop-escalation-gate` - Declared: planned - 人工升级门控：识别无法自动解决的问题、暂停循环状态机、触发人工通知机制
- `loop-progress-persistence` - Declared: planned - 循环进度持久化：记录执行历史、已完成的 change 链、失败/跳过原因、崩溃恢复点与审计日志

## Dependencies

- M4 Agent 生命周期与编排（循环本身作为 agent 运行，依赖状态机和会话管理）
- M22 交互问题解析与多通道回复（Answer Agent 自动回答循环中的问题）
- M7 任务与团队注册表（循环任务注册与管理）
- Roadmap + Milestone 已完整创建（前置条件）

## Risks

- Context 窗口管理不当可能导致循环在长运行后质量下降或产生幻觉
- Answer Agent 在循环场景下可能累积错误假设，导致连续多轮偏离正确方向
- 循环暂停后的恢复点若记录不完整，可能导致重复执行或遗漏 change
- 安全限制配置不当（如无最大循环次数）可能导致资源耗尽或无限循环
- 多 skill 调用链路（recommend → auto 内部再调多个 skill）的错误传播和重试策略复杂度高

## Status
- Declared: proposed

## Notes

- 该里程碑的核心价值是将已有的 recommend / auto / answer-agent 能力串联为端到端的自主开发循环
- Context 重启是关键设计点：每个 recommend → auto 周期结束后应评估是否需要新 session，而非等到 context 硬性截断
- 循环不是简单的 while(true)，而是有明确的状态机：idle → recommending → auto-running → questioning → answering → escalating → paused/stopped
- Answer Agent 的上下文应裁剪为当前 change 相关的最小集合，避免把整个循环历史传入
- 崩溃恢复应基于上一次持久化的 checkpoint，而非重新从头扫描 roadmap 状态
- 首期 SHOULD 支持单线程串行循环，并行循环作为后续扩展

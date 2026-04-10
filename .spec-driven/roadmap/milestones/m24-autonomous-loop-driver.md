# M24 - 内置自主循环执行流水线

## Goal

实现一个内置的自主循环执行流水线，能够基于已创建好的 roadmap 和 milestone 持续自动推进项目开发：按 `recommend → auto` 流程循环选择并执行 change，记录执行进度，并提供可控的启动/暂停/恢复/停止入口，为后续上下文恢复和升级控制能力奠定基础。

## In Scope

- 循环调度器：读取 roadmap/milestone 状态，确定下一个待执行的 change
- Recommend → Auto 执行循环：调用 `roadmap-recommend` 获取推荐 change → 调用 `spec-driven-auto` 自动完成（propose → implement → verify → review → archive）
- 循环进度持久化：每次循环结束记录已完成的 change、当前 milestone 进度、失败/跳过原因
- 循环控制接口：支持启动、暂停、恢复、停止循环；支持配置最大循环次数、单次超时等安全限制

## Out of Scope

- Context 生命周期管理与跨 session 恢复（M26）
- 循环中的 Question 自动答复与人工升级门控（M26）
- Roadmap/Milestone 本身的创建和规划（前置条件，不在本里程碑范围）
- 具体业务代码的实现（由 spec-driven-auto 内部完成）
- 多个循环实例并行运行的资源竞争仲裁
- 分布式多机器协同循环执行

## Done Criteria

- 系统可基于已有 roadmap 启动自主循环，自动按 recommend → auto 流程逐个执行 change
- 循环调度器 MUST 只选择 roadmap 中当前可执行的 change，不得跳过依赖顺序随意推进
- 循环进度 MUST 持久化存储，至少记录当前 change、最近一次执行结果和失败/跳过原因
- 提供启动/暂停/恢复/停止循环的控制接口，支持配置安全限制（最大循环次数、单次超时等）
- 在不涉及 context 切换和问答升级控制的前提下，循环中断后 MUST 能从最近一次已持久化进度继续执行
- 有单元测试覆盖循环调度、recommend → auto 串联、进度持久化、循环控制接口和基础恢复场景

## Planned Changes

- `loop-driver-core` - Declared: planned - 循环调度核心：读取 roadmap 状态机、选择下一个待执行 change、维护循环状态与迭代计数
- `loop-recommend-auto-pipeline` - Declared: planned - Recommend → Auto 执行流水线：串联调用 roadmap-recommend 和 spec-driven-auto skill，处理每轮结果与错误分类
- `loop-progress-persistence` - Declared: planned - 循环进度持久化：记录执行历史、已完成的 change 链、失败/跳过原因、崩溃恢复点与审计日志

## Dependencies

- M4 Agent 生命周期与编排（循环本身作为 agent 运行，依赖状态机和会话管理）
- M7 任务与团队注册表（循环任务注册与管理）
- Roadmap + Milestone 已完整创建（前置条件）

## Risks

- 循环暂停后的恢复点若记录不完整，可能导致重复执行或遗漏 change
- 安全限制配置不当（如无最大循环次数）可能导致资源耗尽或无限循环
- 多 skill 调用链路（recommend → auto 内部再调多个 skill）的错误传播和重试策略复杂度高

## Status
- Declared: proposed

## Notes

- 该里程碑的核心价值是先将已有的 recommend / auto 能力串联为最小可运行的自主执行主流水线
- 循环不是简单的 while(true)，而是有明确的状态机：idle → recommending → auto-running → questioning → answering → escalating → paused/stopped
- M26 再补充 context 切换、Answer Agent 自动回复与人工升级控制，避免本里程碑一次性过载
- 首期 SHOULD 支持单线程串行循环，并行循环作为后续扩展

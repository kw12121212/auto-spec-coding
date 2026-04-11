# M26 - 自主循环恢复与升级控制

## Goal

在 M24 的自主循环执行流水线之上，补齐长运行场景所需的上下文切换、自动答题和人工升级控制能力，使循环在 context 接近耗尽、遇到可自动回答的问题或必须人工介入的问题时，能够安全地恢复、暂停或升级处理。

## In Scope

- Context 生命周期管理：在每个 recommend/auto 周期结束后检测 context 使用量，超阈值时保存状态并启动新 session 继续循环
- 循环中的 Question 自动处理集成：复用 M22 的 Answer Agent 机制，循环中遇到的 question 自动路由给 AI agent 回复
- 人工升级门控：仅当 Answer Agent 标记为无法解决或问题必须人工确认时暂停循环并通知人工
- 与 M24 进度持久化协同的恢复逻辑，确保 context 切换或升级后能返回正确执行点

## Out of Scope

- 自主循环主调度与 recommend → auto 主流水线编排（M24）
- Roadmap/Milestone 本身的创建和规划
- 多个循环实例并行运行的资源竞争仲裁
- 分布式多机器协同循环执行

## Done Criteria

- 每个 recommend/auto 周期完成后，系统可检测 context 使用量，超阈值时保存状态并重启 session 继续循环
- 循环中遇到的 question 可自动路由给 Answer Agent 处理，并在成功答复后恢复执行
- 当问题被标记为必须人工确认或 Answer Agent 无法解决时，循环 MUST 暂停并通知人工
- context 切换、自动答题和人工升级后的恢复 MUST 使用已持久化状态返回正确执行点，不得重复或跳过 change
- 有单元测试覆盖 context 切换、问题自动处理、人工升级门控和恢复执行场景

## Planned Changes
- `context-lifecycle-manager` - Declared: complete - Context 生命周期管理：追踪 token 用量、阈值判断、session 切换与状态恢复
- `loop-answer-agent-integration` - Declared: complete - 循环中 Question 自动处理：对接 M22 Answer Agent 运行时，自动回复循环中产生的 question 并恢复执行
- `loop-escalation-gate` - Declared: complete - 人工升级门控：识别无法自动解决的问题、暂停循环状态机、触发人工通知机制

## Dependencies

- M24 内置自主循环执行流水线，提供基础循环状态机、执行入口和进度持久化
- M22 交互问题解析与多通道回复（Answer Agent 自动回答循环中的问题）
- M19 LLM Streaming & Token Management，如 context 使用量检测依赖统一 token 估算或统计能力

## Risks

- Context 窗口管理不当可能导致循环在长运行后质量下降或产生幻觉
- Answer Agent 在循环场景下可能累积错误假设，导致连续多轮偏离正确方向
- 升级门控和恢复逻辑若处理不严谨，容易造成循环悬挂或错误恢复
- 人工通知链路不稳定时，升级后的等待状态可能持续过久

## Status
- Declared: complete

## Notes

- 该里程碑聚焦长运行自主循环的稳定性与安全性，而不是主流程编排本身
- Context 重启应在每个 recommend → auto 周期结束后评估，而不是等到 context 被硬截断
- Answer Agent 的上下文应裁剪为当前 change 相关的最小集合，避免把整个循环历史传入
- 恢复逻辑应严格基于已持久化 checkpoint，而不是重新从头扫描 roadmap 状态

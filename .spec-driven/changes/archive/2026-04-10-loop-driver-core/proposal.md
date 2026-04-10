# loop-driver-core

## What

实现自主循环调度核心：读取 roadmap/milestone 状态、选择下一个待执行 change、维护循环状态机与迭代计数，提供启动/暂停/恢复/停止的控制接口和可配置的安全限制。

## Why

M24 的目标是让系统基于已有 roadmap 自主推进项目开发。`loop-driver-core` 是整个自主循环的基础——它定义循环如何调度、状态如何流转、安全限制如何执行。后续 `loop-recommend-auto-pipeline`（串联 recommend → auto）和 `loop-progress-persistence`（进度持久化）都依赖此核心提供的状态机和调度能力。

## Scope

- 循环状态机：`IDLE → RECOMMENDING → RUNNING → CHECKPOINT → PAUSED/STOPPED`，含合法转换校验
- 循环调度器：读取 roadmap INDEX 和 milestone 文件，按顺序选出当前 milestone 中下一个 `planned` 状态的 change
- 循环配置：`LoopConfig` 记录，支持最大循环次数、单次超时、安全限制
- 循环控制接口：`LoopDriver` 接口，提供 `start()`、`pause()`、`resume()`、`stop()` 入口
- 循环生命周期事件：在 `EventType` 中新增循环相关事件类型，通过 `EventBus` 发布状态变更
- 迭代计数与当前执行点追踪

## Unchanged Behavior

- 现有 roadmap/milestone 文件格式不变
- 现有 `roadmap-recommend` 和 `spec-driven-auto` skill 的行为不变（本 change 不调用它们）
- 现有 `TaskStore`、`Agent`、`Orchestrator` 接口不变
- 现有 `EventType` 枚举值不变（仅新增）

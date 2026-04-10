# loop-progress-persistence

## What

为自主循环执行流水线（M24）添加持久化存储层，将每次迭代的执行历史、已完成 change 链、失败/跳过原因和循环状态持久化到 Lealone 嵌入式数据库，使循环在进程重启后能从最近一次已持久化的进度继续执行。

## Why

当前 `DefaultLoopDriver` 的迭代记录和已完成 change 列表全部保存在内存中（`ArrayList`），进程退出即丢失。对于长运行的自主循环场景，这意味着：

- 进程崩溃或重启后无法恢复执行进度，只能从头开始
- 已完成的 change 会被调度器重新选中，导致重复执行
- 没有审计日志，无法追溯循环的执行历史和失败原因

M26（自主循环恢复与升级控制）的 context 切换和恢复逻辑也依赖本 change 提供的持久化 checkpoint 能力。

## Scope

**In Scope:**
- 定义 `LoopIterationStore` 接口：迭代记录的保存、查询、恢复
- 实现 `LealoneLoopIterationStore`：基于 Lealone JDBC 的持久化实现
- 定义 `LoopProgress` record：循环级别的进度快照（已完成 change 集合、当前状态、总迭代数）
- 修改 `DefaultLoopDriver`：在每次迭代完成后持久化记录，启动时从存储恢复已完成 change 列表和循环状态
- 发布持久化相关事件（LOOP_PROGRESS_SAVED）

**Out of Scope:**
- Context 生命周期管理与跨 session 恢复（M26）
- 循环中的 Question 自动答复与人工升级门控（M26）
- 分布式多机器协同循环执行
- 多循环实例并行的资源竞争仲裁

## Unchanged Behavior

- `LoopDriver` 接口方法签名不变（`start`、`pause`、`resume`、`stop`、`getState`、`getCurrentIteration`、`getCompletedIterations`、`getConfig`）
- `LoopConfig` record 不变
- `LoopScheduler` / `SequentialMilestoneScheduler` 选择逻辑不变
- `LoopPipeline` / `SpecDrivenPipeline` 执行逻辑不变
- 不接受 Store 的构造函数（两参数、三参数构造函数）行为不变，继续使用内存存储
- 已有的 `LoopState` 状态机转换规则不变

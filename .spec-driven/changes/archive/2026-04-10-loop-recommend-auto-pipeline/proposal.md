# loop-recommend-auto-pipeline

## What

在 `loop-driver-core` 的调度框架之上，实现 Recommend → Auto 执行流水线：将 `SequentialMilestoneScheduler` 的候选选择与 `spec-driven-auto` 的完整工作流（propose → implement → verify → review → archive）串联，使 `DefaultLoopDriver` 的调度循环从桩实现变为真正可执行的自主开发流水线。同时处理每轮执行结果和错误分类。

## Why

`loop-driver-core` 已实现状态机、调度器和控制接口，但 `DefaultLoopDriver.runLoop()` 中的执行阶段是桩——选中候选后立即标记 SUCCESS，不执行任何实际工作。本 change 将循环从"模拟调度"变为"可运行流水线"，是 M24 自主循环执行的核心价值所在。完成后，系统可在配置好 roadmap 的项目上自主推进 change 执行。

## Scope

- `LoopPipeline` 接口：定义流水线执行契约，接受 `LoopCandidate` 返回 `IterationResult`
- `PipelinePhase` 枚举：定义流水线阶段（PROPOSE, IMPLEMENT, VERIFY, REVIEW, ARCHIVE）
- `IterationResult` record：封装单次迭代的执行结果（状态、耗时、失败原因）
- `SpecDrivenPipeline` 实现：按阶段顺序执行 spec-driven 工作流，每阶段使用 Orchestrator + LLM + tools
- 阶段指令模板：每个阶段的系统提示词，作为 classpath 资源加载
- `DefaultLoopDriver` 集成：将桩替换为 `LoopPipeline` 调用，根据 `IterationResult` 更新迭代记录
- 错误分类：将执行异常映射为 `IterationStatus`（SUCCESS, FAILED, SKIPPED, TIMED_OUT）
- 阶段超时：使用 `LoopConfig.iterationTimeoutSeconds` 作为单次迭代的总超时

## Unchanged Behavior

- `LoopDriver` 接口签名不变（start/pause/resume/stop/getState/getCurrentIteration/getCompletedIterations/getConfig）
- `LoopScheduler` 和 `SequentialMilestoneScheduler` 行为不变
- `LoopState` 状态机和转换规则不变
- `LoopConfig` record 字段不变
- 现有 `EventType` 枚举值和 `EventBus` 行为不变
- `SkillServiceExecutor` 和现有 skill 系统不变
- roadmap/milestone 文件格式不变

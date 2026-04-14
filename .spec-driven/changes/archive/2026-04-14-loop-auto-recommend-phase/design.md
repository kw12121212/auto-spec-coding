# Design: loop-auto-recommend-phase

## Approach

把 recommend 建模为 loop pipeline 的第一个阶段，而不是把它继续留在 `DefaultLoopDriver` 的隐式调度代码中。

推荐阶段的输入来自现有 roadmap 文件、target milestone filter 和持久化的 completed change names；输出是一个稳定的 `LoopCandidate`。后续 propose 阶段必须使用同一个 candidate，不得重新选择另一个 planned change。

实现时优先复用现有 `SequentialMilestoneScheduler` 的 roadmap 解析和候选筛选规则，避免产生第二套候选选择语义。recommend 阶段只用于自主 loop 内部无确认路径，不改变手动 `/roadmap-recommend` 技能的用户确认流程。

## Key Decisions

- `RECOMMEND` 是 `PipelinePhase` 的第一个值：阶段顺序需要可审计，且后续 M35 work 会基于 phase 级 checkpoint 和 fresh session 边界恢复执行。
- loop 内 recommend 不等待人工确认：自主循环需要可连续推进；人工确认仍属于手动 `/roadmap-recommend` 的交互语义。
- 候选范围仍由 roadmap declared status 和 completed change names 决定：这样能保持自动和手动规划的候选约束一致。
- recommend 结果必须被后续 propose 使用：避免 recommend 与 propose 之间重新调度导致同一 iteration 内候选漂移。
- 本 change 不引入新的持久化模型：phase 级 checkpoint recovery 属于后续 `loop-phase-checkpoint-recovery`。

## Alternatives Considered

- 继续把 recommend 保持为 `DefaultLoopDriver` 内部调度前置步骤：实现最小，但不可作为 phase 审计，也不利于 M35 后续 session/context boundary 收敛。
- 让 loop 调用完整手动 `/roadmap-recommend` 流程：会卡在用户确认 checkpoint，违背自主循环的无确认执行需求。
- 为 loop 单独实现一套 roadmap 推荐算法：会带来语义漂移风险。复用现有 scheduler 更适合当前阶段。
- 在本 change 同时替换所有 phase 为真实 skill runner：范围过大，应留给 `loop-skill-phase-runner`。

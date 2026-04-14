# loop-auto-recommend-phase

## What

为自主循环增加正式的 roadmap recommend 阶段，使单个 change 的 loop 执行顺序从当前的 `PROPOSE -> IMPLEMENT -> VERIFY -> REVIEW -> ARCHIVE` 扩展为 `RECOMMEND -> PROPOSE -> IMPLEMENT -> VERIFY -> REVIEW -> ARCHIVE`。

该阶段提供仅限 loop 内部使用的无确认推荐路径：loop 可以基于 roadmap 中已有的 planned change 自动选择下一个合法候选，并把该推荐结果作为后续 propose 阶段的权威输入。

## Why

M35 的目标是把自主循环从隐式 prompt 串联收敛为显式 skill phase + 结构化交接。当前 `SequentialMilestoneScheduler` 已能选择候选，但 recommend 只是调度前置行为，不是可审计的 pipeline phase；这会让后续 phase runner、session reset、checkpoint recovery 难以统一记录推荐决策和恢复边界。

先引入 `loop-auto-recommend-phase` 可以把“选哪个 roadmap planned change”纳入 loop 阶段序列，同时不改变手动 `/roadmap-recommend` 对用户显式确认的语义。

## Scope

In scope:

- 将 `RECOMMEND` 纳入 loop phase 顺序，位于 `PROPOSE` 之前。
- 定义 loop 内部无确认 recommend 行为：只能从 roadmap `Planned Changes` 中选择 declared status 为 `planned` 且未完成的 change。
- 为 recommend 阶段提供可审计结果，包含 change name、milestone file、milestone goal 和 planned change summary。
- 确保后续 propose 阶段使用 recommend 阶段选出的同一候选。
- 增加 recommend 阶段模板资源和针对阶段顺序、候选约束、跳过已完成 change 的单元测试。

Out of scope:

- 不改变手动 `/roadmap-recommend` 的确认 checkpoint。
- 不实现完整 `spec-driven-propose/apply/verify/review/archive` skill runner 替换。
- 不实现 phase 级 session reset、checkpoint recovery 或累计 context budget 收敛。
- 不新增外部 SDK、HTTP/JSON-RPC API 或交互式人工处理入口。

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- 手动 `/roadmap-recommend` 仍必须在创建 proposal artifact 前等待用户显式确认。
- Roadmap milestone 和 planned change 文件格式保持不变。
- `LoopScheduler` 对 completed milestone、completed change、target milestone filter 和 recovered completed change names 的选择约束保持不变。
- 现有 `PROPOSE -> IMPLEMENT -> VERIFY -> REVIEW -> ARCHIVE` 阶段在 recommend 之后的相对顺序保持不变。
- 已完成 change 不得因为新增 recommend phase 被重复选择。

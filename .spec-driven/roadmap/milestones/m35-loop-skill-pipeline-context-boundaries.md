# M35 - 自主循环 Skill 化阶段流水线与上下文边界

## Goal

在现有 M24/M26 自主循环能力之上，将 loop 从当前的 scheduler + 内置 phase prompt 执行方式，升级为显式的 `roadmap-recommend -> spec-driven-propose -> spec-driven-apply -> spec-driven-verify -> spec-driven-review -> spec-driven-archive` 阶段化流水线，并为每个阶段建立清晰的 session/context 边界：每步以 fresh context 启动、以文件与持久化状态交接，而不是复用上一阶段的 chat history。

## In Scope

- 显式 Recommend 阶段：在 loop 中引入 roadmap-backed 的推荐步骤，作为 propose 之前的正式 phase
- Loop 内无确认 recommend 路径：为自主循环提供不依赖人工确认的 recommend 执行模式，但仍复用 roadmap 上下文和候选筛选规则
- Skill 化 phase runner：用真实 `spec-driven-*` workflow/skill 执行 propose、apply、verify、review、archive，而不是仅依赖 loop 内置 prompt 模板
- 阶段级 context/session 边界：每个 phase 使用独立 session / conversation / LLM context 启动，只通过 change artifacts、主 specs、repo 状态和 loop 持久化快照传递状态
- 阶段级 checkpoint 与恢复：支持在 recommend、propose、apply、verify、review、archive 任一阶段中断、提问、恢复，并从正确 phase 继续
- 累计 context budget 语义收敛：将 token 使用量的持久化与恢复统一为 loop 级累计值，而不是混入单轮 phase 的临时 chat context

## Out of Scope

- 改变手动 `/roadmap-recommend` 对用户显式确认的默认语义
- 重写 spec-driven skill 自身的 proposal/apply/verify/review/archive 业务规则
- 新的交互式人工处理 UI 或 SQL/NL 交互入口（M29）
- 新的多循环并发调度、分布式执行或跨仓库编排
- 为 phase 之间保留完整 chat transcript 并尝试跨阶段连续推理

## Done Criteria

- loop 在单个 change 上 MUST 按 `roadmap-recommend -> spec-driven-propose -> spec-driven-apply -> spec-driven-verify -> spec-driven-review -> spec-driven-archive` 顺序执行，且阶段顺序可审计
- 每个 phase MUST 以 fresh session/context 启动；phase 间状态交接 MUST 仅依赖磁盘 artifacts、主 specs、repo 当前状态和 loop 持久化快照，而不是上一阶段的对话历史
- loop 内 recommend 阶段 MUST 能在无需人工确认的前提下选择 roadmap 中下一个合法 planned change，并保持与 roadmap 规则一致的候选范围约束
- 当 phase 中断、产生 question、或 loop 被暂停后，恢复逻辑 MUST 能从最近一次已持久化 phase checkpoint 继续，不得错误回退到更早 phase，也不得跳过未完成 phase
- context exhaustion 检测与恢复 MUST 基于 loop 级累计 token 使用量；跨 session 恢复后不得把累计值错误重置为单次 phase 用量
- 自动化测试 MUST 覆盖阶段顺序执行、phase context reset、phase 级恢复、question 暂停/恢复，以及累计 context budget 恢复场景

## Planned Changes
- `loop-auto-recommend-phase` - Declared: complete - 为自主循环增加正式的 roadmap recommend 阶段，并提供仅限 loop 内使用的无确认推荐执行路径
- `loop-skill-phase-runner` - Declared: complete - 用真实 `spec-driven-propose/apply/verify/review/archive` skill 执行器替换现有 loop phase prompt runner，保留可审计的阶段结果
- `loop-phase-session-reset` - Declared: complete - 为 recommend/propose/apply/verify/review/archive 各阶段定义独立 session/context reset 契约，禁止跨阶段复用 chat history 作为权威上下文
- `loop-phase-checkpoint-recovery` - Declared: complete - 扩展 loop 持久化模型到阶段粒度，支持 phase 中断、Question 升级与 resume 后从正确阶段恢复
- `loop-cumulative-context-budget` - Declared: complete - 在已修复当前 `LoopProgress.tokenUsage` 累计持久化基线的前提下，继续收敛 phase 化 skill 流水线中的 context budget、phase checkpoint 与跨 session 预算语义

## Dependencies

- M24 内置自主循环执行流水线（提供基础 loop 状态机、调度与执行入口）
- M26 自主循环恢复与升级控制（提供 question 升级、暂停/恢复与 context 恢复基础）
- M27 智能上下文注入与 Token 优化（提供 phase 内 context 优化能力，但不替代阶段边界）
- M30 动态编译与 Skill 热加载（若 loop 直接执行真实 skill，需要复用既有 skill 运行时）

## Risks

- phase 改为真实 skill 链后，失败模式会从单一 orchestrator 扩展为多阶段、多入口，错误分类与恢复复杂度显著上升
- 若 fresh context 边界定义不清，可能丢失必要状态；若边界过宽，又会退回到长上下文污染问题
- loop 内 recommend 若与手动 `roadmap-recommend` 语义漂移，可能导致自动与人工规划结果不一致
- phase 级 checkpoint 若记录不充分，恢复时容易重复执行 propose/archive 等具有副作用的步骤
- tokenUsage 持久化语义调整若处理不兼容，可能导致旧进度快照恢复异常或 context exhaustion 误判

## Status
- Declared: complete

## Notes

- 该里程碑的核心不是增加更多 loop 功能，而是把已有 loop 流程的边界从“隐式 prompt 串联”收敛为“显式 skill phase + 结构化交接”
- 推荐默认策略应保持“fresh context + files as source of truth”，即每个 phase 开始时重建工作上下文，而不是依赖上一阶段聊天记录
- 手动 `/roadmap-recommend` 仍保留给用户的确认 checkpoint；loop 自动 recommend 需要单独约束其无确认语义，避免影响交互式规划体验
- 如需在未来引入跨 phase 的最小摘要或审计回放，应将其视为持久化结构化元数据，而不是恢复完整对话历史
- 当前代码基线已修复 `DefaultLoopDriver` 将 `LoopProgress.tokenUsage` 误存为单轮值或 `0` 的问题；本 milestone 关注 phase 化改造后的长期语义收敛，不重复把该已修复缺陷当作未开始工作

# Design: loop-escalation-gate

## Approach

在 `DefaultLoopDriver` 的 QUESTIONING 分支中加入一个小型升级判定层。判定层基于已存在的 `Question` 字段工作：`category`、`deliveryMode` 和 `QuestionRoutingPolicy.routingReason()`。它不重新定义问题分类，也不新增独立路由系统。

执行顺序：
- Pipeline 返回 `IterationStatus.QUESTIONING` 后，先发布 `LOOP_QUESTION_ROUTED`
- 读取 `Question.category()` 和 `Question.deliveryMode()`
- 如果 category 是 `PERMISSION_CONFIRMATION` 或 `IRREVERSIBLE_APPROVAL`，或者 delivery mode 不是 `AUTO_AI_REPLY`，立即进入人工升级路径
- 只有 `AUTO_AI_REPLY` 且 category 允许自动回答时，才调用 `LoopAnswerAgent`
- 升级路径发布补强后的 `LOOP_QUESTION_ESCALATED`，记录 partial iteration，保存 progress，然后转入 `PAUSED`
- `resume()` 保持现有 `PAUSED → RECOMMENDING` 行为，调度器根据持久化的 completed changes 继续选择候选，不把暂停的 change 误标为完成

人工通知复用已有 question delivery surface。若配置了 `QuestionDeliveryService`，升级问题应能通过其配置的 channel 发送；若未配置 delivery service，循环至少通过事件和 pending question 元数据暴露人工处理所需信息。

## Key Decisions

- **升级优先于 Answer Agent**：human-only category 和 human delivery mode 代表外部确认要求，必须在调用 AI 前拦截。
- **复用现有 QuestionRoutingPolicy**：分类与 delivery mode 规则已经在 M22/M23 定义，循环层只消费这些结果，避免产生第二套路由真相源。
- **不把升级的 change 标记为完成**：partial iteration 记录 `QUESTIONING` 状态和 failure reason，但 `completedChangeNames` 不应加入该 change，恢复后仍可处理同一候选。
- **事件携带完整人工处理上下文**：`LOOP_QUESTION_ESCALATED` 必须包含 category、deliveryMode、routingReason 和 sessionId，便于日志、通知和后续交互层消费。
- **通知可选但可观测性必需**：delivery service 可能未配置；这种情况下行为不能失败，但事件和 driver 状态仍必须可证明循环已升级并暂停。

## Alternatives Considered

- **所有 QUESTIONING 都先交给 Answer Agent**：被否决。权限确认和不可逆审批不能由 AI 自动代答。
- **新增独立的 LoopEscalationStore**：被否决。M24/M26 已有 `LoopIterationStore` 和 question storage，首期不需要额外持久化通道。
- **直接进入 M29 交互式会话**：被否决。M29 依赖稳定的暂停/升级基础；本变更只完成升级门控，不引入 SQL/NL 交互。
- **升级时将当前 change 标记为失败完成**：被否决。这会导致恢复时跳过仍需人工确认的 change，违反 M26 恢复到正确执行点的目标。

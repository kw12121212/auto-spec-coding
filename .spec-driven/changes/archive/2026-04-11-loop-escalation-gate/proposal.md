# loop-escalation-gate

## What

为自主循环执行流水线增加人工升级门控，使 `DefaultLoopDriver` 在遇到不能自动回答的问题时进入可恢复的暂停状态，而不是继续调用 AI Answer Agent 或只留下不可操作的 `QUESTIONING` 结果。

具体交付内容：
- 定义循环级升级判定规则：`PERMISSION_CONFIRMATION`、`IRREVERSIBLE_APPROVAL` 以及非 `AUTO_AI_REPLY` delivery mode 的问题 MUST 直接进入人工升级路径
- 增强 `DefaultLoopDriver` 的 QUESTIONING 处理：先判定是否需要人工升级，只有可自动回答的问题才调用 `LoopAnswerAgent`
- 增强升级事件元数据：包含 `questionId`、`sessionId`、`changeName`、`category`、`deliveryMode`、`reason` 和 `routingReason`
- 升级后持久化 partial iteration 与 loop progress，使 `resume()` 能从已保存进度继续，而不重复或跳过已完成 change
- 对接已有 question delivery surface，保证人工处理问题可被通知或暴露给配置的人工答复通道
- 单元测试覆盖 human-only category、human delivery mode、answer-agent escalation、无 answer agent、持久化与恢复路径

## Why

M24 已完成 recommend → auto 主循环，M26 已完成 context 生命周期管理和 Answer Agent 集成。当前剩余风险在于：循环虽然能在 AI 可回答问题上恢复执行，但对于必须人工确认的问题，缺少明确的升级门控和通知契约。

没有这个门控时，生产写入确认、不可逆审批、权限确认等问题可能被错误地交给 AI 自动回答，或者只让循环停在 `PAUSED` 状态但缺少足够的元数据供人工处理和恢复。`loop-escalation-gate` 用最小范围补齐 M26 的最后一块安全能力，为后续 M29 的交互式 human-in-loop 提供稳定基础。

## Scope

**In scope：**
- 循环级人工升级判定规则
- `DefaultLoopDriver` 在 `IterationStatus.QUESTIONING` 时的升级优先级：human-only 问题优先升级，不调用 `LoopAnswerAgent`
- `LOOP_QUESTION_ESCALATED` 事件元数据补强
- 升级暂停时的 partial `LoopIteration`、`LoopProgress` 持久化与 `resume()` 恢复要求
- 与已有 `QuestionDeliveryService` / `QuestionRuntime` 的通知或 pending-question 暴露契约
- 单元测试和验证任务

**Out of scope：**
- 新增移动渠道适配器；M23 已覆盖内置移动渠道
- 新增交互式 SQL/NL 会话；属于 M29
- 改写 Answer Agent 生成逻辑；本变更只决定是否调用它
- 多个循环实例并行仲裁
- HTTP/JSON-RPC 新端点

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- 可自动回答的 `CLARIFICATION` / `PLAN_SELECTION` + `AUTO_AI_REPLY` 问题仍走 `LoopAnswerAgent` 路径
- `LoopAnswerAgent` 返回 `Resolved` 时仍从已完成 phases 后恢复 pipeline
- `LoopAnswerAgent` 返回 `Escalated` 或未配置时仍暂停循环
- `LoopConfig`、`LoopPipeline`、`IterationResult` 的已有公共签名保持兼容
- 无 Question 的 pipeline 执行路径不变

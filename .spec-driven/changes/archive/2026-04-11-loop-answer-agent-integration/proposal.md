# loop-answer-agent-integration

## What

在自主循环执行流水线（M24）中集成 M22 的问题解析运行时（QuestionRuntime），使循环在 pipeline 执行过程中遇到 Question 时，自动路由给 AI Answer Agent 处理，收到 ANSWER_ACCEPTED 答复后从中断点恢复执行，而不是停转等待人工介入。

具体交付内容：
- `LoopAnswerAgent` 接口：定义循环内部问题路由的统一契约
- `DefaultLoopAnswerAgent`：实现，构造专用 AI agent，调用 M22 `QuestionRuntime` 提交答复
- `LoopState.QUESTIONING` 新状态及其合法转换路径
- `IterationStatus.QUESTIONING` 新枚举值，表示当前迭代因问题路由而中断
- `IterationResult` 新增可选 `question` 字段，携带触发中断的 Question 实例
- `DefaultLoopDriver` 问题路由编排逻辑：RUNNING → QUESTIONING → RUNNING（成功）或 QUESTIONING → PAUSED（升级）
- 新增 EventType：`LOOP_QUESTION_ROUTED`、`LOOP_QUESTION_ANSWERED`、`LOOP_QUESTION_ESCALATED`

## Why

M24 建立了 recommend → auto 的自主执行主流水线，M26 的目标是让这条流水线在长运行场景下保持稳定可用。当前循环中任何 LLM 操作只要触发 Question，流水线就会返回失败，整个循环暂停，必须人工介入。这严重削弱了自主性，与 M24 的核心价值相悖。

本变更完成 M26 的关键一步：让循环能够自动消化绝大多数 AI 可回答的问题，只在真正无法自动解决时才升级到人工（由后续 `loop-escalation-gate` 变更处理）。

## Scope

**In scope：**
- `LoopAnswerAgent` 接口及 `DefaultLoopAnswerAgent` 实现
- `LoopState.QUESTIONING` 新状态与合法转换规则扩展
- `IterationStatus.QUESTIONING` 新值
- `IterationResult.question` 可选字段
- `SpecDrivenPipeline` 的 EventBus 问题捕获逻辑（监听 QUESTION_CREATED，中断 pipeline 并携带 Question 返回）
- `DefaultLoopDriver` 问题路由编排：检测 QUESTIONING 结果 → 调用 LoopAnswerAgent → 根据决策恢复或升级
- 同步阻塞调用模式，超时阈值复用 `LoopConfig.iterationTimeoutSeconds()`
- 新 EventType 常量（`LOOP_QUESTION_ROUTED`、`LOOP_QUESTION_ANSWERED`、`LOOP_QUESTION_ESCALATED`）
- 单元测试覆盖：问题路由成功恢复、超时升级、ESCALATE_TO_HUMAN 升级、Answer Agent 异常处理

**Out of scope：**
- 人工升级门控与暂停通知（`loop-escalation-gate` 变更）
- 移动端推送通知渠道（属于 M22/M23 范畴）
- 同一迭代中并发多个 Question 的处理（首期仅支持单 Question 串行）
- Answer Agent 本身的 LLM 选型与 prompt 工程细节（Answer Agent 复用 LoopConfig 的 LLM 配置）

## Unchanged Behavior

- 现有 `LoopState` 的所有已有状态及其合法转换路径不变
- `LoopDriver` 的 `start()`、`pause()`、`resume()`、`stop()` 语义不变
- `IterationStatus.SUCCESS`、`FAILED`、`SKIPPED`、`TIMED_OUT` 的含义不变
- `SpecDrivenPipeline` 在无 Question 事件时的执行路径不变
- `DefaultLoopDriver` 在不传入 `LoopAnswerAgent` 时的行为不变（向后兼容）
- M22 `QuestionRuntime` 的公共接口不变，本变更仅作为调用方使用

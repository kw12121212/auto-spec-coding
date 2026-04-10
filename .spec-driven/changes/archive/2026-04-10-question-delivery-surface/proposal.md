# question-delivery-surface

## What

为 M22 question-resolution 的最后一个功能缺口补充三个层面：

1. **交付层抽象接口** — `QuestionDeliveryChannel`（推送问题通知到外部渠道）和 `QuestionReplyCollector`（接收外部人工回复），由 `QuestionDeliveryService` 作为 facade 组合两者
2. **持久化 QuestionStore** — 基于 Lealone 的独立 question 存储，支持按 session、按 status 查询和超时扫描，使 SDK 层可从外部查询 pending questions 并注入回复
3. **SDK 层 question API** — 在 `SdkAgent` 上暴露 `pendingQuestions()`、`submitHumanReply()`、`configureDeliveryMode()` 方法，并在 `SdkBuilder` 上支持全局 delivery mode 覆盖配置

## Why

前四个 change 已完成 question 合约、编排暂停、answer agent 运行时和路由策略，但 question 的交付和外部回复回流仍是空白。没有交付层抽象，`PUSH_MOBILE_WAIT_HUMAN` 和 `PAUSE_WAIT_HUMAN` 模式只能通过内部 `QuestionRuntime` 的内存队列模拟，无法被 SDK 外部消费者使用。完成此 change 后 M22 整体里程碑可标记为 complete，同时解除 M23 移动交互集成层的阻塞。

## Scope

### In Scope

- `QuestionDeliveryChannel` 接口 — push 通知到外部渠道（无厂商绑定）
- `QuestionReplyCollector` 接口 — 接收并验证外部人工回复
- `QuestionDeliveryService` facade — 组合上述两者，作为 SDK 层的统一入口
- `QuestionStore` 接口和 `LealoneQuestionStore` 实现 — 持久化 question 状态
- `SdkAgent.pendingQuestions()` — 查询当前 session 的待答问题
- `SdkAgent.submitHumanReply()` — 注入人工回复并触发恢复
- `SdkBuilder.deliveryModeOverride()` — 全局 delivery mode 覆盖配置
- 单元测试覆盖交付、存储、SDK 层功能

### Out of Scope

- 具体移动推送厂商适配（APNs、FCM、企业 IM）— 属于 M23
- JSON-RPC / HTTP 传输层的 pending-question API — 属于 M13/M14 扩展
- Per-session delivery mode 覆盖 — 记为 future enhancement
- 多 answer agent 竞争仲裁

## Unchanged Behavior

- 现有 `QuestionRuntime` 的内存等待逻辑和单 session 单 question 限制不变
- 现有 `QuestionRoutingPolicy` 默认路由映射不变，全局覆盖仅在此基础上提供 override
- `SdkAgent.run()` 的同步执行语义不变
- 已有的 `AnswerAgentRuntime` 自动回复链路不变

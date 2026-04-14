# loop-question-interactive-bridge

## What

在 DefaultLoopDriver 因人工升级问题暂停时，自动创建 InteractiveSession 进入交互模式，使操作者可通过自然语言或 SQL 与系统交互、查看状态并提交答复。交互结束后通过既有 resume() 路径恢复循环执行。

## Why

M29 的前两个 change（interactive-session-interface、lealone-agent-adapter）已完成交互会话接口和 Lealone 适配器的实现，但 LoopDriver 尚未接入交互模式。当前人工升级路径仅暂停循环并等待外部恢复，操作者无法在暂停期间通过结构化交互查看状态或提交指令。本 change 将已有的 InteractiveSession 能力桥接到 LoopDriver 的升级暂停点，是 M29 剩余工作的关键前置依赖。

## Scope

- 扩展 DefaultLoopDriver：在人工升级暂停时创建 InteractiveSession，提供交互入口
- 新增 InteractiveSessionFactory 接口：将 InteractiveSession 创建策略参数化，使 DefaultLoopDriver 不直接依赖具体实现
- 交互输入通过已有 QuestionDeliveryService.submitReply() 路由为等待中 Question 的 Answer
- 交互操作审计日志通过已有 AuditLogStore 记录
- 新增 LOOP_INTERACTIVE_ENTERED / LOOP_INTERACTIVE_EXITED EventType

## Unchanged Behavior

- InteractiveSession 接口签名和生命周期不变（NEW → ACTIVE → CLOSED / ERROR）
- LoopDriver 接口签名不变（start / pause / resume / stop / getState / getCurrentIteration / getCompletedIterations / getConfig）
- 现有 PAUSED → RECOMMENDING 的 resume() 路径不变
- 人工升级的 question 路由策略不变（QuestionRoutingPolicy、DeliveryMode 判定逻辑不变）
- LoopAnswerAgent 的自动答复路径不变
- 未配置 InteractiveSessionFactory 时，行为与当前完全一致（仅暂停、不进入交互模式）

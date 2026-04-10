# Tasks: question-delivery-surface

## Implementation

- [x] 定义 `QuestionStore` 接口 — save, update, findBySession, findByStatus, findPending, delete 方法
- [x] 实现 `LealoneQuestionStore` — 遵循 LealoneTaskStore 模式：自动建表、EventBus 事件、后台超时扫描线程
- [x] 定义 `QuestionDeliveryChannel` 接口 — send(question), close() 方法
- [x] 实现 `LoggingDeliveryChannel` — 默认实现，将 question payload 输出到日志
- [x] 定义 `QuestionReplyCollector` 接口 — collect(sessionId, questionId, answer), onClose() 方法
- [x] 实现 `InMemoryReplyCollector` — 默认实现，基于内存队列的回复收集
- [x] 实现 `QuestionDeliveryService` facade — 组合 DeliveryChannel + ReplyCollector + QuestionRuntime + QuestionStore
- [x] 在 `QuestionRuntime` 中集成 QuestionStore — beginWaitingQuestion/acceptAnswer/expireQuestion 时同步写入 store
- [x] 在 `SdkBuilder` 新增 `deliveryModeOverride(DeliveryMode)` 配置项
- [x] 在 `SdkAgent` 新增 `pendingQuestions(sessionId)` 方法
- [x] 在 `SdkAgent` 新增 `submitHumanReply(sessionId, questionId, answer)` 方法
- [x] 在 `SdkAgent.SdkInternalAgent.buildOrchestratorConfig()` 中传递 delivery mode 覆盖
- [x] 在 `SpecDriven` 中持有 `QuestionDeliveryService` 引用并传递给 `SdkAgent`

## Testing

- [x] Run `mvn compile -pl . -q` to validate compilation
- [x] Run `mvn test -pl . -Dtest=LealoneQuestionStoreTest` unit tests for QuestionStore CRUD and timeout scan
- [x] Run `mvn test -pl . -Dtest=QuestionDeliveryServiceTest` unit tests for DeliveryService facade
- [x] Run `mvn test -pl . -Dtest=SdkAgentQuestionTest` unit tests for SDK pendingQuestions/submitHumanReply
- [x] Run `mvn test -pl . -Dtest=LoggingDeliveryChannelTest` unit tests for default delivery channel
- [x] Run `mvn test -pl . -Dtest=InMemoryReplyCollectorTest` unit tests for default reply collector

## Verification

- [x] 所有新增测试通过
- [x] 现有 question-runtime/answer-agent 测试不受影响
- [x] `mvn compile` 无 warning
- [x] delta spec 覆盖所有新增接口和 SDK 方法

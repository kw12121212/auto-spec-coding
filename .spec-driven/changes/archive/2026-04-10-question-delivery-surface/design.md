# Design: question-delivery-surface

## Approach

分三层实现，每层有独立接口，由 Lealone 存储层贯穿：

1. **存储层** — 新建 `QuestionStore` 接口和 `LealoneQuestionStore`，遵循现有 `LealoneTaskStore` 的 JDBC 模式：自动建表、EventBus 事件发布、后台超时扫描线程。`QuestionRuntime` 在 accept/expire/close 时同步写入 store。

2. **交付层** — 定义 `QuestionDeliveryChannel`（单向推送）和 `QuestionReplyCollector`（回复收集）两个独立接口，由 `QuestionDeliveryService` facade 组合。交付服务在 `QuestionRuntime.beginWaitingQuestion` 之后被调用推送，在 `submitHumanReply` 时将回复注入 runtime。首期提供 `LoggingDeliveryChannel` 和 `InMemoryReplyCollector` 两个默认实现用于测试和本地调试。

3. **SDK 层** — 在 `SdkBuilder` 上新增 `deliveryModeOverride(DeliveryMode)` 配置项，影响 `SdkInternalAgent` 的编排配置。在 `SdkAgent` 上新增 `pendingQuestions(sessionId)` 和 `submitHumanReply(sessionId, questionId, answer)` 方法，直接委托给 `QuestionStore` 和 `QuestionDeliveryService`。

### 数据流

```
SdkAgent.run()
  -> DefaultOrchestrator question pause
    -> QuestionRuntime.beginWaitingQuestion()
      -> QuestionStore.save(WAITING_FOR_ANSWER)
      -> QuestionDeliveryService.deliver(question)  // push to channel
        -> QuestionDeliveryChannel.send(question)
    -> (waits for answer via QuestionRuntime.pollAnswer)

SdkAgent.submitHumanReply(sessionId, questionId, answer)
  -> QuestionReplyCollector.collect(answer)
  -> QuestionRuntime.submitAnswer(sessionId, questionId, answer)
  -> QuestionRuntime.acceptAnswer() -> QuestionStore.update(ANSWERED)
  -> (orchestrator resumes)
```

## Key Decisions

1. **拆分交付和收集接口** — 推送和回复是方向相反的数据流，合并会迫使实现者同时实现两套逻辑。拆开后 M23 的具体渠道适配器只需实现自己关心的那一半。

2. **独立 QuestionStore 而非复用 SessionStore** — Question 有独立的生命周期和查询模式（按 status 过滤、按 session 过滤、超时扫描），混在 session store 里会污染 session 的职责边界。遵循现有 task/team/cron 的独立 store 模式。

3. **首期仅全局 delivery mode 覆盖** — Per-session 覆盖增加测试组合和状态管理复杂度，M22 done criteria 没有要求此粒度。M23 的渠道配置自然会引入更细的粒度。

4. **QuestionStore 与 QuestionRuntime 并行存在** — `QuestionRuntime` 继续管理内存中的等待队列和阻塞逻辑，`QuestionStore` 负责持久化和外部查询。两者在状态变更时同步，不合并为单一类。

## Alternatives Considered

1. **单一 QuestionDeliveryService 接口** — 被否决。推送和回复生命周期不同，单一接口违反 ISP。

2. **将 QuestionStore 嵌入 SessionStore** — 被否决。Session store 职责已定（session 元数据），加入 question 查询会使跨 session 统计困难。

3. **立即支持 Per-session delivery mode** — 被否决。首期交付优先完成 M22，M23 会自然引入渠道级配置。

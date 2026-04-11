# Design: loop-answer-agent-integration

## Approach

### 整体数据流

```
DefaultLoopDriver
  └─ RUNNING → pipeline.execute(candidate, config)
       └─ SpecDrivenPipeline
            ├─ 订阅 EventBus QUESTION_CREATED 事件
            ├─ 正常完成 → IterationResult(SUCCESS)
            └─ 捕获 Question → IterationResult(QUESTIONING, question=<Q>)

DefaultLoopDriver 收到 QUESTIONING 结果
  ├─ 发布 LOOP_QUESTION_ROUTED
  ├─ 切换状态 RUNNING → QUESTIONING
  └─ loopAnswerAgent.resolve(question, timeoutSeconds)
       └─ DefaultLoopAnswerAgent
            ├─ 构造专用 AI Agent，prompt = question 结构化内容
            ├─ 运行轻量 Orchestrator（单轮 LLM 调用）
            └─ 调用 QuestionRuntime.submitAnswer(sessionId, answer)
                 ├─ ANSWER_ACCEPTED → 发布 LOOP_QUESTION_ANSWERED
                 │    → 状态 QUESTIONING → RUNNING
                 │    → 重新执行被中断 phase（skipPhases = 已完成阶段）
                 └─ ESCALATE_TO_HUMAN / TIMEOUT → 发布 LOOP_QUESTION_ESCALATED
                      → 状态 QUESTIONING → PAUSED
                      → 由后续 loop-escalation-gate 变更处理
```

### SpecDrivenPipeline 问题捕获

`SpecDrivenPipeline.execute()` 在进入每个 phase 前，通过 EventBus 注册一次性 `QUESTION_CREATED` 监听器。当 orchestrator 内部触发 `QUESTION_CREATED` 时，pipeline 捕获该 Question，中止当前 phase，返回 `IterationResult(status=QUESTIONING, question=<Q>, phasesCompleted=<到中断点前已完成的阶段>)`。

pipeline 新增可选 `Set<PipelinePhase> skipPhases` 参数（仅重试路径使用），跳过已完成阶段，从中断点继续。

### LoopAnswerAgent 接口

```java
public interface LoopAnswerAgent {
    AnswerResolution resolve(Question question, int timeoutSeconds);
}
```

`AnswerResolution` 为密封接口，区分两种结果：
- `Resolved(Answer answer)` — 答复已提交，决策为 ANSWER_ACCEPTED
- `Escalated(String reason)` — Answer Agent 判定需人工介入或超时

### DefaultLoopAnswerAgent 实现

- 接受 `LlmClient` 和 `QuestionRuntime`
- 构造 prompt：包含 `question.question()`、`question.impact()`、`question.recommendation()`，要求输出结构化答复内容
- 运行轻量 Orchestrator（单轮 LLM 调用，不携带工具集）
- 解析答复，构造 `Answer(source=AI_AGENT, decision=ANSWER_ACCEPTED, ...)`
- 调用 `QuestionRuntime.submitAnswer()` 提交答复
- 超时通过 `ExecutorService.invokeAny()` + timeoutSeconds 实现；超时返回 `Escalated("timeout")`

### DefaultLoopDriver 编排扩展

- 新增构造器重载，接受可选 `LoopAnswerAgent answerAgent`（null = 不处理问题，向后兼容）
- pipeline 返回 QUESTIONING 时：
  1. 发布 `LOOP_QUESTION_ROUTED`
  2. 切换 RUNNING → QUESTIONING
  3. 调用 `answerAgent.resolve(result.question(), config.iterationTimeoutSeconds())`
  4. `Resolved` → 切换 QUESTIONING → RUNNING，重新 execute pipeline（skipPhases = result.phasesCompleted()）
  5. `Escalated` → 切换 QUESTIONING → PAUSED，记录 failureReason，等待 loop-escalation-gate

## Key Decisions

**同步阻塞 + iterationTimeoutSeconds 超时**
与现有 pipeline 超时机制保持一致，不引入额外配置字段。超时行为作为升级触发条件，与后续 loop-escalation-gate 保持语义一致。

**LoopAnswerAgent 接口隔离**
不直接在 DefaultLoopDriver 中依赖 QuestionRuntime，通过接口解耦。便于测试（可 mock LoopAnswerAgent）且不破坏现有依赖图。

**QUESTIONING → PAUSED 而非 QUESTIONING → ERROR**
问题无法自动解决不是"错误"，是预期的升级场景，PAUSED 语义更准确且与 loop-escalation-gate 的入口状态对齐。

**phase 重试而非 iteration 重试**
如果整个 iteration 重试，已完成 phase 的副作用（如文件写入）会被重复执行，存在幂等性风险。从被中断 phase 重试更安全。

**向后兼容：answerAgent 为 null 时 QUESTIONING = PAUSED**
不传 LoopAnswerAgent 时，如果 pipeline 返回 QUESTIONING，driver 直接转 PAUSED，保持现有不处理问题的行为，不引入 breaking change。

## Alternatives Considered

**异步回调模式（Answer Agent 通过 EventBus 答复）**
降低了循环线程阻塞时间，但引入复杂的回调状态机，难以控制超时和恢复点。当前规模不值得这个复杂度。

**Answer Agent 共享 SpecDrivenPipeline 的 LLM 上下文**
传入整个 conversation history 给 Answer Agent 可能提高回答质量，但将 SpecDrivenPipeline 内部状态泄漏给 LoopAnswerAgent，违反关注点分离。首期仅传入结构化 question 字段。

**在 LoopConfig 增加独立 questionTimeoutSeconds 字段**
增加配置复杂度，而问题路由超时语义与迭代超时一致，没有必要分开配置。有真实需求时再扩展。

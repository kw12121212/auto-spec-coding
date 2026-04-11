# Autonomous Loop — Delta Spec: loop-answer-agent-integration

## MODIFIED Requirements

### Requirement: LoopState enum — QUESTIONING 新状态

- `LoopState` MUST add state `QUESTIONING`
- 新增合法转换路径：
  - `RUNNING → QUESTIONING`（pipeline 返回 QUESTIONING 结果，问题路由中）
  - `QUESTIONING → RUNNING`（LoopAnswerAgent 返回 Resolved，重新执行 pipeline）
  - `QUESTIONING → PAUSED`（LoopAnswerAgent 返回 Escalated 或无 answerAgent 配置）
  - `QUESTIONING → ERROR`（LoopAnswerAgent 抛出未预期异常）
- 已有状态及其现有转换路径 MUST NOT 改变

### Requirement: IterationStatus enum — QUESTIONING 新值

- `IterationStatus` MUST add value `QUESTIONING`
- `QUESTIONING` 表示该迭代因 pipeline 中检测到 Question 而中断，答复路由中
- 已有值 `SUCCESS`、`FAILED`、`SKIPPED`、`TIMED_OUT` 的含义 MUST NOT 改变

### Requirement: IterationResult record — question 字段

- `IterationResult` MUST add an optional field `question` of type `Question` (nullable)
- `question` MUST be non-null when `status == QUESTIONING`；其他状态下 MUST be null
- 已有字段 `status`、`failureReason`、`durationMs`、`phasesCompleted` 的契约 MUST NOT 改变
- `StubLoopPipeline` MUST return `IterationResult` with `question=null`

### Requirement: LoopPipeline — skipPhases 重试参数

- `LoopPipeline` MUST add method `execute(LoopCandidate candidate, LoopConfig config, Set<PipelinePhase> skipPhases)` returning `IterationResult`
- 当 `skipPhases` 为空集时，行为 MUST 与无参数版本一致
- 当 `skipPhases` 非空时，`SpecDrivenPipeline` MUST skip those phases and start from the first non-skipped phase in `PipelinePhase.ordered()`
- 原有 `execute(LoopCandidate, LoopConfig)` MUST remain as a default method delegating to the new overload with an empty set

### Requirement: SpecDrivenPipeline — 问题捕获逻辑

- `SpecDrivenPipeline.execute()` 在每个 phase 的 orchestrator 执行期间 MUST subscribe to `QUESTION_CREATED` events on the EventBus
- 当 `QUESTION_CREATED` 事件在某 phase 执行期间触发时，MUST abort that phase and return `IterationResult(status=QUESTIONING, question=<captured question>, phasesCompleted=<phases completed before the interrupted phase>)`
- 若某 phase 正常完成且未触发 `QUESTION_CREATED`，执行路径 MUST NOT change
- EventBus 监听器 MUST be unregistered after each phase completes or is aborted

## ADDED Requirements

### Requirement: AnswerResolution sealed interface

- MUST be a sealed interface in `org.specdriven.agent.loop` with two permitted implementations:
  - `Resolved(Answer answer)` — record；`answer` MUST be non-null；表示 LoopAnswerAgent 已成功提交答复
  - `Escalated(String reason)` — record；`reason` MUST be non-null；表示问题需升级，不能自动解决
- `AnswerResolution` MUST NOT expose any mutable state

### Requirement: LoopAnswerAgent interface

- MUST be a public interface in `org.specdriven.agent.loop`
- MUST define `resolve(Question question, int timeoutSeconds)` returning `AnswerResolution`
- MUST NOT throw checked exceptions — all failures MUST be captured in the returned `AnswerResolution`
- When `timeoutSeconds` is exceeded, MUST return `Escalated("timeout")`
- Implementation MUST NOT modify the Question object

### Requirement: DefaultLoopAnswerAgent

- MUST implement `LoopAnswerAgent` in `org.specdriven.agent.loop`
- Constructor MUST accept `LlmClient llmClient` and `QuestionRuntime questionRuntime`
- `resolve()` MUST construct a prompt from `question.question()`, `question.impact()`, and `question.recommendation()`
- MUST run a single-turn LLM call (no tools) with the prompt within the specified timeout using a virtual thread
- MUST parse the LLM response and construct an `Answer` with `source=AI_AGENT`, `decision=ANSWER_ACCEPTED`, `confidence` derived from response content (default 0.8 when not parseable), `answeredAt=System.currentTimeMillis()`
- MUST call `questionRuntime.submitAnswer(question.sessionId(), question.questionId(), answer)` after constructing the Answer
- When the LLM call or `submitAnswer` throws, MUST return `Escalated("<exception message>")`
- When timeout is exceeded, MUST interrupt the virtual thread and return `Escalated("timeout")`

### Requirement: DefaultLoopDriver — LoopAnswerAgent 集成

- MUST add constructor overload `DefaultLoopDriver(LoopConfig, LoopScheduler, LoopPipeline, LoopIterationStore, LoopAnswerAgent)` where `LoopAnswerAgent` MAY be null
- When pipeline returns `IterationResult` with `status=QUESTIONING`:
  - MUST transition state `RUNNING → QUESTIONING`
  - MUST publish `LOOP_QUESTION_ROUTED` event with metadata: `questionId` (String), `changeName` (String)
  - When `loopAnswerAgent` is non-null: MUST call `loopAnswerAgent.resolve(result.question(), config.iterationTimeoutSeconds())`
    - On `Resolved`: MUST transition `QUESTIONING → RUNNING`, then re-invoke `pipeline.execute(candidate, config, result.phasesCompleted())` to resume from the interrupted phase
    - On `Escalated`: MUST transition `QUESTIONING → PAUSED`, record `failureReason = resolution.reason()`
  - When `loopAnswerAgent` is null: MUST transition `QUESTIONING → PAUSED` with `failureReason = "no answer agent configured"`
- Existing constructors MUST remain valid with `loopAnswerAgent=null`

### Requirement: Loop EventType additions — 问题路由事件

- MUST add the following values to the existing `EventType` enum in `org.specdriven.agent.event`:
  - `LOOP_QUESTION_ROUTED` — published when a question is detected and routing begins; metadata: `questionId` (String), `changeName` (String), `sessionId` (String)
  - `LOOP_QUESTION_ANSWERED` — published when `LoopAnswerAgent` returns `Resolved`; metadata: `questionId` (String), `changeName` (String), `confidence` (double)
  - `LOOP_QUESTION_ESCALATED` — published when `LoopAnswerAgent` returns `Escalated` or is absent; metadata: `questionId` (String), `changeName` (String), `reason` (String)
- Existing `EventType` values MUST NOT change

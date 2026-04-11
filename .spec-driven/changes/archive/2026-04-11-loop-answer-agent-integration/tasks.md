# Tasks: loop-answer-agent-integration

## Implementation

- [x] 在 `LoopState` 枚举中添加 `QUESTIONING` 状态，并在 `isValidTransition()` 中补充四条转换规则：`RUNNING→QUESTIONING`、`QUESTIONING→RUNNING`、`QUESTIONING→PAUSED`、`QUESTIONING→ERROR`
- [x] 在 `IterationStatus` 枚举中添加 `QUESTIONING` 值
- [x] 在 `IterationResult` record 中添加可选字段 `question`（`Question` 类型，nullable），并在 compact constructor 中允许 null；`StubLoopPipeline` 返回 `question=null`
- [x] 在 `LoopPipeline` 接口中新增 `execute(LoopCandidate, LoopConfig, Set<PipelinePhase>)` 重载，原有 `execute(LoopCandidate, LoopConfig)` 改为 default 方法委托到新重载（空集）
- [x] 在 `SpecDrivenPipeline.execute()` 中实现 QUESTION_CREATED 事件捕获：每个 phase 执行前注册一次性 EventBus 监听器，触发时中止 phase 并返回 `IterationResult(QUESTIONING, question, phasesCompleted)`；phase 完成后取消监听器
- [x] 在 `SpecDrivenPipeline.execute(candidate, config, skipPhases)` 中实现 phase 跳过逻辑：按 `PipelinePhase.ordered()` 遍历，跳过 `skipPhases` 中的阶段，从第一个未跳过的 phase 开始执行
- [x] 在 `org.specdriven.agent.loop` 包中创建 `AnswerResolution` 密封接口，包含 `Resolved(Answer)` 和 `Escalated(String)` 两个 permitted record
- [x] 在 `org.specdriven.agent.loop` 包中创建 `LoopAnswerAgent` 接口，定义 `resolve(Question, int)` 方法
- [x] 实现 `DefaultLoopAnswerAgent`：接受 `LlmClient` 和 `QuestionRuntime`，构造 prompt，运行单轮 LLM 调用（virtual thread + ExecutorService 超时），构造 Answer，调用 `QuestionRuntime.submitAnswer()`，异常和超时均返回 `Escalated`
- [x] 在 `EventType` 枚举中添加 `LOOP_QUESTION_ROUTED`、`LOOP_QUESTION_ANSWERED`、`LOOP_QUESTION_ESCALATED` 三个新值
- [x] 在 `DefaultLoopDriver` 中添加新构造器重载（接受 `LoopAnswerAgent`，允许 null），并在调度循环中增加 QUESTIONING 结果处理分支：发布 `LOOP_QUESTION_ROUTED`，切换状态，调用 answerAgent，按 Resolved/Escalated 分别恢复或暂停

## Testing

- [x] 运行 lint/validation 检查：`mvn checkstyle:check -q`
- [x] 运行 unit tests：`mvn test -pl . -Dtest="LoopStateTest,IterationResultTest,DefaultLoopDriverTest,DefaultLoopAnswerAgentTest,SpecDrivenPipelineTest,LoopAnswerAgentIntegrationTest" -q`
- [x] `LoopStateTest`：验证 `QUESTIONING` 的四条合法转换，以及从 QUESTIONING 到其他非法状态时抛 `IllegalStateException`
- [x] `IterationResultTest`：验证 `status=QUESTIONING` 时 `question` 非 null；其他状态时 `question` 为 null
- [x] `DefaultLoopAnswerAgentTest`：mock `LlmClient` 和 `QuestionRuntime`，覆盖正常答复（返回 Resolved）、超时（返回 Escalated("timeout")）、`submitAnswer` 抛异常（返回 Escalated）三个场景
- [x] `SpecDrivenPipelineTest`：mock EventBus，验证捕获 QUESTION_CREATED 后返回 `IterationResult(QUESTIONING)`，且 `phasesCompleted` 只包含中断前已完成的阶段；验证 skipPhases 正确跳过已完成阶段
- [x] `LoopAnswerAgentIntegrationTest`（`DefaultLoopDriverTest` 中新增场景）：
  - pipeline 返回 QUESTIONING + mock answerAgent 返回 Resolved → 验证循环恢复执行，状态经过 RUNNING→QUESTIONING→RUNNING，最终迭代以 SUCCESS 完成
  - pipeline 返回 QUESTIONING + mock answerAgent 返回 Escalated → 验证循环进入 PAUSED，发布 LOOP_QUESTION_ESCALATED
  - pipeline 返回 QUESTIONING + answerAgent 为 null → 验证循环进入 PAUSED，failureReason 含 "no answer agent configured"
  - 验证 LOOP_QUESTION_ROUTED、LOOP_QUESTION_ANSWERED、LOOP_QUESTION_ESCALATED 三个事件在正确时机发布

## Verification

- [x] 确认 `LoopState` 现有转换规则未被修改，仅新增 QUESTIONING 相关规则
- [x] 确认 `IterationStatus` 现有四个值含义未变
- [x] 确认现有 `DefaultLoopDriver` 的两参数、三参数、四参数构造器仍可正常使用（向后兼容）
- [x] 确认 `LoopPipeline.execute(candidate, config)` default 方法正确委托到新重载（空 skipPhases）
- [x] 确认 `StubLoopPipeline` 返回 `question=null` 且 `status=SUCCESS`，现有行为不变
- [x] 确认 proposal.md 的所有 In Scope 条目均有对应实现任务或已完成
- [x] 确认 proposal.md 的 Out of Scope 条目（升级门控、移动推送、并发多 Question）未被实现

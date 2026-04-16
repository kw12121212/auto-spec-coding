# Tasks: workflow-service-composition

## Implementation

- [x] 新增 `WorkflowStep` record，字段为 `StepType type`（枚举：SERVICE/TOOL/AGENT）和 `String name`，在构造时校验 `name` 非空非空白
- [x] 新增 `WorkflowStepResult` record，包含步骤执行的输出 `Map<String, Object> output` 和可选的 `String failureReason`
- [x] 新增 `WorkflowStepExecutor` 接口，签名为 `WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input)`
- [x] 在 `EventType` 枚举中新增 `WORKFLOW_STEP_STARTED`、`WORKFLOW_STEP_COMPLETED`、`WORKFLOW_STEP_FAILED` 三个事件类型
- [x] 将 `WorkflowRuntime.declarations` 的值类型从 `String` 改为 `WorkflowDeclaration` record（包含 workflowName 和不可变步骤列表）
- [x] 在 `WorkflowRuntime` 构造函数中接受 `List<WorkflowStepExecutor>` 并按 `StepType` 建立分发 map
- [x] 改写 `WorkflowRuntime.advanceWorkflow`：顺序迭代步骤，发布 `WORKFLOW_STEP_STARTED` 事件，调用对应执行器，合并输出到下步输入，失败时发布 `WORKFLOW_STEP_FAILED` 并调用 `fail()`，全部完成后以最后步骤输出调用 `succeed()`
- [x] 声明路径校验：步骤描述符中 `name` 为空白时 `declareWorkflow` / `declareWorkflowSql` 必须抛出 `IllegalArgumentException`

## Testing

- [x] build validate: `mvn compile -q`
- [x] run unit tests: `mvn test -pl . -Dtest=WorkflowStepCompositionTest -q`
- [x] 新增 `WorkflowStepCompositionTest`，覆盖以下场景：
  - [x] 单步 tool 工作流：步骤执行后工作流到达 SUCCEEDED，结果为步骤输出
  - [x] 多步顺序执行：验证步骤按声明顺序调用，前步输出出现在后步输入中
  - [x] 步骤失败传播：第一步失败后第二步不被调用，工作流到达 FAILED，failure summary 包含失败步骤信息
  - [x] 无步骤工作流：兼容性验证，不调用任何执行器，工作流到达 SUCCEEDED
  - [x] 步骤名称为空时声明失败（domain 路径）
  - [x] 步骤审计事件：WORKFLOW_STEP_STARTED / WORKFLOW_STEP_COMPLETED / WORKFLOW_STEP_FAILED 各场景下事件均正确发布
- [x] 运行全量测试确认无回归：`mvn test -q`

## Verification

- [x] 确认 `WorkflowRuntimeTest` 原有测试全部通过，无步骤工作流行为未被破坏
- [x] 确认 `WorkflowStepCompositionTest` 覆盖 proposal.md 中的全部可观察场景
- [x] 确认 delta spec 中所有 ADDED/MODIFIED 需求均有对应测试场景覆盖
- [x] 确认无新增外部依赖（Lealone-first 原则）

# Design: workflow-service-composition

## Approach

在现有 `WorkflowRuntime` 的基础上做最小扩展：

1. **步骤描述符** — 引入 `WorkflowStep` record，字段为 `type`（枚举：SERVICE/TOOL/AGENT）和 `name`（目标名称）。声明时可附带零到多个步骤。
2. **声明存储扩展** — `declarations` map 的值从简单字符串改为 `WorkflowDeclaration` record，包含 workflow 名称和步骤列表（不可变）。
3. **步骤执行器** — 引入 `WorkflowStepExecutor` 接口，由 `WorkflowRuntime` 构造时注入。每种步骤类型对应一个实现：`ServiceStepExecutor`、`ToolStepExecutor`、`AgentStepExecutor`。接口签名为：`Map<String, Object> execute(WorkflowStep step, Map<String, Object> input)`。
4. **`advanceWorkflow` 改写** — 依次迭代步骤列表，调用对应执行器；将上一步输出合并进下一步输入；任意步骤抛出异常则调用 `fail(record, reason)` 并返回；全部步骤通过则将最后一步输出作为 `succeed` 的结果。
5. **步骤审计事件** — 在现有 `EventType` 中新增 `WORKFLOW_STEP_STARTED`、`WORKFLOW_STEP_COMPLETED`、`WORKFLOW_STEP_FAILED` 三个事件类型，在执行器调用前后各发布。

## Key Decisions

- **数据驱动步骤声明**：步骤在声明时内联描述，而非运行时注册，与已建立的 SQL `CREATE WORKFLOW` 路径保持一致，并支持未来序列化存储。
- **输入合并语义**：上步输出以 `Map.copyOf` 方式与工作流原始输入合并，后者优先级更低；下步收到的是合并后的 Map，保持不可变。
- **顺序执行**：本变更只支持顺序执行；并行/条件分支留给后续变更，避免过早引入复杂编排模型。
- **执行器接口注入**：`WorkflowRuntime` 通过构造函数接受 `WorkflowStepExecutor` 列表，不内部耦合具体实现，便于测试时替换为 stub 执行器。
- **无步骤兼容**：步骤列表为空时，`advanceWorkflow` 直接 `succeed`，与现有行为完全兼容。

## Alternatives Considered

- **运行时注册步骤处理器（代码驱动）**：由应用代码在运行时注册每个工作流的步骤执行逻辑。被否决：与 SQL 声明路径不兼容，且难以序列化持久化。
- **将步骤执行逻辑内联在 `WorkflowRuntime` 中**：避免引入 `WorkflowStepExecutor` 接口。被否决：难以在单元测试中替换为 stub，且违反单一职责。
- **立即支持并行步骤**：被否决：超出本变更范围（YAGNI），并行步骤依赖检查点恢复能力，应由后续 `workflow-recovery-audit` 提供。

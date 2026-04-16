# workflow-service-composition

## What

扩展工作流运行时，使单个工作流能够在声明时内联描述一个有序的步骤列表，并在执行时依次分发到 service、tool 或 agent 层，将每步的输出作为下一步的输入，并在步骤失败时将工作流标记为 FAILED。

## Why

`workflow-runtime-contract` 建立了工作流的声明、启动、状态查询和生命周期契约，但当前 `advanceWorkflow` 是占位实现，工作流无法执行任何实质内容。本变更补齐步骤组合执行能力，使工作流从"可声明"变为"可执行"，形成 service/tool/agent 与人工协作的业务执行闭环的基础。

## Scope

**In scope:**
- 工作流声明契约扩展：支持在声明时内联描述有序步骤列表，每步声明类型（service/tool/agent）和目标名称
- 步骤顺序执行：步骤按声明顺序依次执行，前步输出作为后步输入
- 步骤分发：将 service 步骤分发到现有 service 运行层，tool 步骤分发到 Tool 接口，agent 步骤分发到 Orchestrator/AgentContext
- 步骤结果传递：每步结果以 Map<String, Object> 形式传递给下一步
- 步骤失败传播：任何步骤失败立即终止工作流，转为 FAILED，failure summary 标识失败步骤及原因
- 步骤审计：每步的启动、完成和失败各发布一条审计事件
- 无步骤工作流兼容：声明时不含步骤的工作流仍可正常执行并以 SUCCEEDED 结束

**Out of scope:**
- 并行步骤执行、条件分支、循环
- 步骤间数据转换或映射表达式
- 跨步骤检查点与恢复（由后续 `workflow-recovery-audit` 负责）
- 人工介入与暂停恢复（由 `workflow-agent-human-bridge` 负责）
- 步骤超时与重试策略

## Unchanged Behavior

- 工作流声明、启动、状态查询、结果读取、生命周期状态流转契约完全保持不变
- 已有无步骤声明路径（domain/SQL）仍可正常运行，工作流执行完毕后到达 SUCCEEDED
- EventType.WORKFLOW_DECLARED、WORKFLOW_STARTED、WORKFLOW_STATE_CHANGED、WORKFLOW_COMPLETED、WORKFLOW_FAILED 事件语义不变

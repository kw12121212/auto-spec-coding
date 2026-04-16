# M37 - 企业级工作流运行与治理

## Goal

承接 Lealone README 中 `workflow` 直接运行企业级应用的方向，在已有 agent、question-resolution、interactive human-in-loop、mobile delivery 和 loop 恢复能力之上，补齐面向业务流程的工作流运行时、恢复和治理能力，使 service、tool、agent 与人工协作可以形成可观测、可恢复的企业级执行闭环。

## In Scope

- 工作流声明、启动、状态查询、结果回传的最小可观察契约
- 工作流内 service、tool、agent 步骤的组合执行与阶段结果传递
- 工作流中的提问、人工介入、移动端回复与交互式恢复桥接
- 工作流级失败诊断、审计记录、重试与恢复边界
- 与现有 loop、question、event audit、session store 的职责边界梳理

## Out of Scope

- BPMN 级通用流程设计器或可视化编排平台
- 任意第三方 SaaS 编排连接器市场
- 多租户工作流隔离模型
- 分布式多节点工作流调度平台
- 把已有 spec-driven 研发流程 loop 直接重写为企业业务工作流引擎

## Done Criteria

- 系统 MUST 提供工作流的声明、启动、状态查询和结果读取的可观察契约
- 工作流 MUST 能组合执行受支持的 service/tool/agent 步骤，并对每一步的输入、输出和失败状态形成可审计记录
- 当工作流进入需要人工确认或补充信息的阶段时，系统 MUST 能复用现有 question/mobile/interactive 能力完成暂停、提问和恢复
- 工作流恢复机制 MUST 在受支持的失败场景下从正确的持久化检查点继续，而不是错误重跑已确认完成的步骤
- 工作流级观测 MUST 至少覆盖状态流转、失败原因、人工介入记录和最终结果
- 自动化测试 MUST 覆盖基础 happy path、人工介入暂停恢复、失败审计、可恢复错误重试和不可恢复错误终止场景

## Planned Changes
- `workflow-runtime-contract` - Declared: complete - 定义工作流的声明、启动、状态查询、结果回传与状态流转契约，建立面向业务流程的最小运行面
- `workflow-service-composition` - Declared: complete - 支持在单个工作流中组合执行 service、tool 与 agent 步骤，明确步骤间输入输出和失败传播语义
- `workflow-agent-human-bridge` - Declared: complete - 将现有 question resolution、mobile reply 和 interactive session 能力接入工作流暂停、提问、恢复链路
- `workflow-recovery-audit` - Declared: complete - 提供工作流级审计、失败诊断、检查点恢复与受支持错误重试能力

## Dependencies

- M22 交互问题解析与多通道回复（工作流提问与人工回复）
- M23 配置化移动交互集成层（移动端介入与送达观测）
- M24 内置自主循环执行流水线（可复用阶段执行与持久化经验）
- M26 自主循环恢复与升级控制（恢复与升级控制基础）
- M29 SQL+自然语言交互式人机协作（交互式恢复入口）
- M36 Lealone 原生服务应用运行时（提供面向业务应用的 service 运行底座）

## Risks

- 若直接复用研发流程 loop 语义，可能把 spec-driven 变更流水线和业务工作流错误耦合在一起
- 人工介入链路若缺乏清晰超时、重试和责任边界，工作流容易长期悬挂
- 检查点恢复若记录粒度不足，可能在恢复时重复执行已产生副作用的 service 步骤
- 工作流运行时若过早追求通用编排平台，会超出当前仓库的 Lealone-centered 范围

## Status
- Declared: complete

## Notes

- 本里程碑对应 Lealone README 里 `create workflow` 所代表的增强方向，但会优先落在本项目现有能力已经证明可行的范围内：service、agent、人机协作、审计、恢复
- 这里的 workflow 是业务应用运行时，不是 spec-driven change 生命周期本身；两者可共享基础设施，但不应共享同一套语义边界
- 若后续需要多节点调度、租户隔离或复杂流程建模，应另开专门 milestone，而不是继续扩张本里程碑





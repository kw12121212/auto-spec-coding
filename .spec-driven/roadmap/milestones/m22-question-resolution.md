# M22 - 交互问题解析与多通道回复

## Goal

为 agent 提供通用的 question-handling 能力：当主 agent 在运行中遇到需要外部答复的问题时，能够生成结构化 question payload，并按配置选择由副 AI agent 自动回复、推送到移动设备等待人工回复、或直接停在当前会话等待人工交互，再在收到答复后恢复执行。

## In Scope

- Question / Answer / Escalation 的统一领域模型与状态机
- 标准化 question payload，至少包含 `question`、`impact`、`recommendation`
- 主 agent 在编排循环中的提问、暂停、等待答复、恢复执行机制
- Answer Agent 运行时：以隔离上下文分析问题并返回结构化答复
- 问题路由策略：区分可 AI 代答、推送移动端等待人工、直接停机等待人工的问题
- 移动推送通知抽象接口与人工回复回流机制
- 问题生命周期的会话持久化、审计记录与来源标记
- SDK 层的模式配置、待答问题查询与人工答复注入能力

## Out of Scope

- 用 AI 自动放行权限确认、不可逆写操作批准或安全审批
- 具体移动推送厂商或 App 协议绑定（APNs、FCM、企业 IM 等）
- JSON-RPC / HTTP 传输层的 pending-question API 暴露
- 多个 answer agent 并行竞争同一问题的仲裁机制
- 面向具体 skill 的问题模板库或领域知识库

## Done Criteria

- 主 agent 可在运行中生成结构化 question 并进入等待答复状态
- question payload MUST 至少包含 `question`、`impact`、`recommendation` 三个字段
- 对于标记为可自动解析的问题，系统可启动 answer agent 并生成结构化 answer 返回给主 agent
- 对于配置为移动推送人工回复的问题，系统可发送通知到移动设备并在人工回复后恢复执行
- 对于配置为原地等待人工交互的问题，系统 MUST 停止自动推进并等待人工答复
- 对于标记为必须人工确认的问题，系统 MUST 升级为人工答复，不得由 AI 自动闭环
- 主 agent 在接收 AI 或人工答复后可恢复执行，并把答复写入会话历史
- 每条答复均可追溯来源、依据摘要、置信度和升级原因
- 有单元测试覆盖 AI 代答、移动推送等待人工、原地等待人工、超时、恢复执行和审计落盘场景

## Planned Changes
- `question-contract-audit` - Declared: complete - 定义 Question、Answer、AnswerSource、QuestionStatus、QuestionDecision、DeliveryMode 等核心类型、question payload 规范、事件模型以及审计字段
- `orchestrator-question-pause` - Declared: planned - 扩展 DefaultOrchestrator：支持提问、暂停等待、接收答复、超时与恢复执行
- `answer-agent-runtime` - Declared: planned - 实现副 AI agent 的创建、上下文裁剪、答复生成与结构化返回链路
- `question-routing-policy` - Declared: planned - 定义问题分类、默认回复模式与升级策略，明确哪些问题可 AI 代答，哪些问题需推送移动端等待人工，哪些问题必须原地人工答复
- `question-delivery-surface` - Declared: planned - 定义移动推送抽象接口、人工回复回流机制，以及 Native Java SDK 层的模式配置、pending question 查询、人工答复提交与恢复执行接口

## Dependencies

- M4 Agent 生命周期与编排（暂停、恢复、会话状态保持）
- M5 LLM Provider Layer（answer agent 生成答复）
- M6 权限模型与执行钩子（区分可代答与必须人工确认的交互）
- M12 Native Java SDK 层（暴露待答问题和人工回复入口）
- Event audit log 与 session store（问题生命周期追踪）

## Risks

- 如果问题分类边界不清，AI 代答可能误覆盖本应人工审批的高风险交互
- 移动推送到达失败、重复送达或人工回复延迟会导致会话长时间悬挂
- answer agent 使用共享上下文时，可能把主 agent 的错误假设进一步放大
- 问题等待与恢复机制会增加编排状态复杂度，处理不当容易造成悬挂会话
- 审计字段过弱会导致后续无法解释“为什么由 AI 回答了这个问题”

## Status

- Declared: proposed

## Notes

- 该里程碑面向通用 agent 运行时，不绑定单一 skill 或 CLI 工作流
- Answer Agent 应使用裁剪后的最小必要上下文，避免把整段对话无差别复制过去
- 回复模式至少应支持：`auto_ai_reply`、`push_mobile_wait_human`、`pause_wait_human`
- 问题分类至少应区分：澄清型问题、方案选择建议、权限确认、不可逆操作批准
- 移动推送首期只定义抽象接口和回流契约，不绑定具体移动平台或推送厂商
- M11 的 skill 执行引擎后续可复用该能力，但 M22 不依赖 M11 才能成立


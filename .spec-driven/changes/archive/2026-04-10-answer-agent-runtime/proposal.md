# answer-agent-runtime

## What

实现副 AI Agent（Answer Agent）运行时，用于自动处理标记为 `AUTO_AI_REPLY` 交付模式的问题。当主 Agent 在运行中遇到需要外部答复的问题时，Answer Agent 能够以隔离上下文分析问题并返回结构化答复，无需人工介入。

主要组件：
- `AnswerAgent` - 副 AI Agent 接口定义
- `AnswerAgentRuntime` - Answer Agent 运行时管理器，负责创建、执行和销毁 Answer Agent 实例
- `ContextWindowManager` - 上下文裁剪器，从主 Agent 会话中提取最小必要上下文
- `AnswerGenerationService` - 答复生成服务，协调 LLM 调用和结构化 Answer 构建

## Why

M22 里程碑旨在为 Agent 提供通用的 question-handling 能力。目前已有：
- Question/Answer 核心类型和状态机（complete）
- Orchestrator 的提问、暂停、等待答复机制（complete）
- 问题路由策略（complete）

但尚缺少 **AI 自动回复** 能力。当前实现中，`AUTO_AI_REPLY` 交付模式会抛出异常（见 `DefaultOrchestrator.beginWaitingQuestion`），必须由 Answer Agent Runtime 接管处理。

实现此变更后：
1. 澄清型问题（CLARIFICATION）和方案选择（PLAN_SELECTION）可由 AI 自动答复
2. 减少人工介入频率，提升 Agent 自动化程度
3. 为 M23 移动交互层提供完整的 question-resolution 能力基础

## Scope

### In Scope

- `AnswerAgent` 接口定义（创建、运行、销毁生命周期）
- `AnswerAgentRuntime` 运行时管理器（实例池、超时控制、错误处理）
- `ContextWindowManager` 上下文裁剪策略（提取问题相关上下文，避免整段对话复制）
- `AnswerGenerationService` 答复生成服务（调用 LLM、解析响应、构建结构化 Answer）
- Answer Agent 配置参数（模型选择、温度、最大 token、超时时间）
- 与 `DefaultOrchestrator` 集成：当 `deliveryMode == AUTO_AI_REPLY` 时触发 Answer Agent
- 单元测试覆盖：正常答复、上下文裁剪、超时、错误恢复、审计字段完整性

### Out of Scope

- 多 Answer Agent 并行竞争同一问题的仲裁机制（M22 明确排除）
- 权限确认和不可逆操作的 AI 代答（由 `QuestionRoutingPolicy` 保证不路由到 AUTO_AI_REPLY）
- 移动推送通知实现（属于 M23）
- HTTP/JSON-RPC 层的 pending-question API（属于 `question-delivery-surface`）

## Unchanged Behavior

- `Question`、`Answer` 核心类型的字段和验证逻辑保持不变
- `QuestionStatus`、`DeliveryMode`、`AnswerSource`、`QuestionDecision` 枚举值保持不变
- `QuestionRuntime` 的等待问题生命周期管理保持不变
- `QuestionRoutingPolicy` 的路由规则保持不变
- `DefaultOrchestrator` 对 `PUSH_MOBILE_WAIT_HUMAN` 和 `PAUSE_WAIT_HUMAN` 的处理逻辑保持不变
- 每个会话同时只能有一个等待问题的限制保持不变

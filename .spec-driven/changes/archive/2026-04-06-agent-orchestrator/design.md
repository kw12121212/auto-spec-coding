# Design: agent-orchestrator

## Approach

1. 定义 `LlmClient` 接口作为 LLM 调用的抽象层（M5 提供真实实现），方法 `chat(List<Message>)` 返回 `LlmResponse`
2. 定义 `ToolCall` record（toolName: String, parameters: Map<String, Object>）表示 LLM 请求执行的工具
3. 定义 `LlmResponse` sealed interface，两个子类型：`TextResponse`（纯文本回复）和 `ToolCallResponse`（包含 List<ToolCall>）
4. 定义 `Orchestrator` 接口，核心方法 `run(AgentContext, LlmClient)`
5. 实现 `DefaultOrchestrator`，循环逻辑：
   - **receive**: 从 Conversation 获取完整消息历史
   - **think**: 调用 `LlmClient.chat(history)` 获取 LlmResponse，将 assistant 回复追加到 Conversation
   - **act**: 若 LlmResponse 为 ToolCallResponse，从 toolRegistry 查找 Tool 并执行；多个 ToolCall 按 list 顺序串行执行（保证前一个结果可供后续参考）
   - **observe**: 将 ToolResult 封装为 ToolMessage 追加到 Conversation（无论成功或失败），继续循环；Tool 执行失败不终止循环，而是将错误反馈给 LLM
   - 终止条件：TextResponse（无更多工具调用）、达到最大轮数、agent 状态非 RUNNING
6. 覆写 `DefaultAgent.doExecute`：创建 Orchestrator 并委托执行
7. `OrchestratorConfig` 提供 maxTurns（默认 50）、toolTimeout（默认 120s）等配置项

## Key Decisions

- **LlmClient 作为接口而非具体实现**：orchestrator 不依赖 M5 的具体 provider，测试用 mock，M5 完成后直接注入
- **ToolCall 是独立 record 而非复用 ToolInput**：ToolCall 携带 LLM 语义信息，与 Tool 执行的 ToolInput 职责不同
- **编排循环覆写 DefaultAgent.doExecute**：不改变 Agent 接口签名，通过 doExecute hook 注入
- **最大轮数作为安全阀**：防止 LLM 无限工具调用循环，默认 50 轮
- **ToolCalls 按 list 顺序串行执行**：保证前一个结果可供后续 ToolCall 参考，满足依赖关系

## Alternatives Considered

- **编排逻辑直接写在 DefaultAgent 中**：放弃，职责不清且不利于测试和替换策略
- **使用 CompletableFuture 并行工具调用**：VirtualThread 更简洁，项目已明确 JDK 25 基线
- **LlmClient 返回原始 JSON 由 orchestrator 解析**：增加 orchestrator 职责，不如让 LlmClient 负责协议适配
- **并行执行多个 ToolCall**：用户要求顺序/依赖机制，改为串行执行，并行可后续 opt-in
- **Tool 失败时终止循环**：改为反馈错误给 LLM，支持自修复

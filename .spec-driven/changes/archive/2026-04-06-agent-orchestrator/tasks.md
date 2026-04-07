# Tasks: agent-orchestrator

## Implementation

- [x] 定义 `ToolCall` record（toolName: String, parameters: Map<String, Object>）在 `org.specdriven.agent.agent` 包
- [x] 定义 `LlmResponse` sealed interface 及 `TextResponse`、`ToolCallResponse` 子类型在 `org.specdriven.agent.agent` 包
- [x] 定义 `LlmClient` 接口（`chat(List<Message>)` 返回 `LlmResponse`）在 `org.specdriven.agent.agent` 包
- [x] 定义 `Orchestrator` 接口（`run(AgentContext, LlmClient)`）在 `org.specdriven.agent.agent` 包
- [x] 定义 `OrchestratorConfig` record（maxTurns、toolTimeout）在 `org.specdriven.agent.agent` 包
- [x] 实现 `DefaultOrchestrator`：receive → think → act → observe 循环，顺序工具执行
- [x] 覆写 `DefaultAgent.doExecute`：创建 Orchestrator 并委托执行

## Testing

- [x] `mvn compile` — lint and validation
- [x] `mvn test` — unit tests: ToolCallTest, LlmResponseTest, DefaultOrchestratorTest, DefaultAgentOrchestratorTest

## Verification

- [x] 实现与 proposal scope 一致：不改变 Agent/Tool/Conversation 已有接口

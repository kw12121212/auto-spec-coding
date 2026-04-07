# Agent Interface Spec — agent-orchestrator delta

## ADDED Requirements

### Requirement: LlmClient interface

- MUST define `chat(List<Message>)` returning `LlmResponse`
- MUST be a provider-agnostic abstraction — no provider-specific types in the interface
- MAY define additional methods for streaming or model selection in future changes

### Requirement: LlmResponse sealed interface

- MUST be a sealed interface in `org.specdriven.agent.agent` permitting exactly two subtypes: `TextResponse` and `ToolCallResponse`
- `TextResponse` MUST carry `content` (String) — the LLM's text reply
- `ToolCallResponse` MUST carry `toolCalls` (List<ToolCall>) — one or more tool invocations requested by the LLM
- Both subtypes MUST be Java records

### Requirement: ToolCall record

- MUST be a Java record in `org.specdriven.agent.agent` with fields: `toolName` (String), `parameters` (Map<String, Object>)
- MUST defensively copy the parameters map in the compact constructor

### Requirement: Orchestrator interface

- MUST define `run(AgentContext, LlmClient)` executing the full think-act-observe loop
- MUST be callable from `DefaultAgent.doExecute`

### Requirement: DefaultOrchestrator implementation

- MUST implement the Orchestrator interface in `org.specdriven.agent.agent`
- MUST follow the loop: receive conversation history → call LlmClient → if ToolCallResponse, execute tools → append results to Conversation → repeat
- MUST terminate when LlmClient returns TextResponse (no tool calls)
- MUST terminate when the maximum turn count is reached
- MUST terminate when the agent state is no longer RUNNING
- MUST execute ToolCalls from a single ToolCallResponse in list order (sequential) — earlier results are available to subsequent tool lookups via Conversation
- MUST append an AssistantMessage to Conversation for each LlmResponse before acting on tool calls
- MUST append a ToolMessage to Conversation for each tool execution result, including errors — Tool execution failure MUST NOT terminate the loop; the error MUST be fed back as a ToolMessage for LLM self-repair
- MUST construct ToolInput from ToolCall parameters and execute via Tool.execute
- MUST be a public class in `org.specdriven.agent.agent`

### Requirement: OrchestratorConfig

- MUST be a Java record with `maxTurns` (int, default 50) and `toolTimeoutSeconds` (int, default 120)
- MUST provide a static factory `defaults()` returning the default configuration
- MUST be accepted by DefaultOrchestrator constructor

### Requirement: DefaultAgent doExecute delegation

- `DefaultAgent.doExecute(AgentContext)` MUST create a DefaultOrchestrator and delegate execution
- MUST construct OrchestratorConfig from the agent's config map if present, otherwise use defaults

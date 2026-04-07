# Agent Interface Spec (delta)

## MODIFIED Requirements

### Requirement: DefaultOrchestrator implementation

- MUST implement the Orchestrator interface in `org.specdriven.agent.agent`
- MUST follow the loop: receive conversation history → call LlmClient → if ToolCallResponse, execute tools → append results to Conversation → repeat
- MUST terminate when LlmClient returns TextResponse (no tool calls)
- MUST terminate when the maximum turn count is reached
- MUST terminate when the agent state is no longer RUNNING
- MUST return immediately if conversation is null or LlmClient is null
- MUST execute ToolCalls from a single ToolCallResponse in list order (sequential) — earlier results are available to subsequent tool lookups via Conversation
- MUST append an AssistantMessage to Conversation for each LlmResponse before acting on tool calls
- MUST append a ToolMessage to Conversation for each tool execution result, including errors — Tool execution failure MUST NOT terminate the loop; the error MUST be fed back as a ToolMessage for LLM self-repair
- MUST construct ToolInput from ToolCall parameters and execute via Tool.execute
- MUST be a public class in `org.specdriven.agent.agent`
- **NEW:** Before invoking `tool.execute()`, MUST run `beforeExecute` on each registered `ToolExecutionHook` in list order
- **NEW:** If any hook's `beforeExecute` returns `ToolResult.Error`, MUST skip `tool.execute()` and use the hook's error as the result
- **NEW:** After successful tool execution, MUST run `afterExecute` on each registered hook

### Requirement: OrchestratorConfig

- MUST be a Java record with `maxTurns` (int, default 50), `toolTimeoutSeconds` (int, default 120), and `hooks` (List<ToolExecutionHook>, default empty list)
- MUST provide a static factory `defaults()` returning the default configuration
- MUST provide a static factory `fromMap(Map<String, String>)` constructing config from key-value pairs with fallback to defaults
- MUST be accepted by DefaultOrchestrator constructor

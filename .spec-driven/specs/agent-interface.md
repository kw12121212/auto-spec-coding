# Agent Interface Spec

## ADDED Requirements

### Requirement: Agent lifecycle

- MUST follow a lifecycle: init → start → (execute) → stop → close
- MUST track its state via `getState()` returning `AgentState`
- SHOULD integrate with Lealone plugin lifecycle conventions (init/start/stop/close) by pattern, not inheritance

### Requirement: AgentState enum

- MUST define states: IDLE, RUNNING, PAUSED, STOPPED, ERROR
- Each state MUST be independently testable

### Requirement: AgentContext

- MUST provide a session identifier
- MUST provide access to configuration
- SHOULD provide access to a tool registry

### Requirement: Agent state transitions

- MUST enforce the following valid transitions: IDLE→RUNNING (via start), RUNNING→STOPPED (via stop), RUNNING→PAUSED (when orchestrator suspends execution waiting for a question answer), RUNNING→ERROR (on uncaught exception in execute), PAUSED→RUNNING (when the waiting question receives an accepted answer before timeout), PAUSED→STOPPED (via stop), ERROR→STOPPED (via stop)
- MUST reject any transition not listed above by throwing IllegalStateException with a descriptive message
- MUST treat STOPPED as a terminal state — no transition away from STOPPED is allowed

#### Scenario: Waiting question pauses the agent
- GIVEN an agent run that raises a structured question requiring deferred external input
- WHEN the orchestrator enters wait mode for that question
- THEN the agent state MUST transition from `RUNNING` to `PAUSED`

#### Scenario: Accepted answer resumes the paused agent
- GIVEN an agent in `PAUSED` state because one question is waiting for an answer
- WHEN a matching answer is accepted before the wait timeout expires
- THEN the agent state MUST transition from `PAUSED` back to `RUNNING`

#### Scenario: Resume is rejected without a waiting question
- GIVEN an agent session that has no unresolved waiting question
- WHEN a resume attempt is made
- THEN the system MUST reject the attempt
- AND the agent state MUST remain unchanged

### Requirement: Agent init behavior

- MUST accept configuration via init(Map<String, String> config) and store it for later access
- MUST transition to IDLE state after successful init
- MUST NOT allow init to be called more than once — second call MUST throw IllegalStateException

### Requirement: Agent start behavior

- MUST transition from IDLE to RUNNING when start() is called
- MUST throw IllegalStateException if start() is called in any state other than IDLE

### Requirement: Agent execute behavior

- MUST only be callable when the agent is in RUNNING state
- MUST throw IllegalStateException if called in any state other than RUNNING
- MUST automatically transition to ERROR state if execute throws an uncaught exception
- MUST accept an AgentContext parameter providing session ID, config, and tool registry

### Requirement: Agent stop behavior

- MUST transition to STOPPED when called from RUNNING, PAUSED, or ERROR states
- MUST throw IllegalStateException if called from IDLE or STOPPED state

### Requirement: Agent close behavior

- MUST be callable from any state
- MUST release all held resources
- MUST result in STOPPED state after close completes

### Requirement: DefaultAgent implementation

- MUST be a concrete class implementing the Agent interface in the org.specdriven.agent.agent package
- MUST follow the Lealone plugin lifecycle pattern (init/start/stop/close) by method naming convention

### Requirement: Message sealed interface

- MUST be a sealed interface in `org.specdriven.agent.agent` permitting exactly four subtypes: UserMessage, AssistantMessage, ToolMessage, SystemMessage
- Each subtype MUST be a Java record with fields: `content` (String), `timestamp` (long, epoch millis)
- MUST expose `role()` returning a String identifying the message origin: "user", "assistant", "tool", "system"
- MUST be immutable — records enforce this by definition

### Requirement: Message subtypes

- `UserMessage` — role "user", represents human input
- `AssistantMessage` — role "assistant", represents agent/LLM output
- `ToolMessage` — role "tool", represents tool execution result; MUST additionally carry `toolName` (String) field
- `SystemMessage` — role "system", represents system-level instructions

### Requirement: Conversation class

- MUST be a public class in `org.specdriven.agent.agent`
- MUST support `append(Message)` to add a message to history
- MUST support `history()` returning an unmodifiable list of all messages in insertion order
- MUST support `get(int index)` returning the message at the given position; MUST throw IndexOutOfBoundsException for invalid indices
- MUST support `size()` returning the message count
- MUST support `clear()` removing all messages
- MUST be thread-safe for concurrent append and read operations

### Requirement: AgentContext conversation access

- `AgentContext` MUST provide a `conversation()` method returning the current `Conversation`
- For backward compatibility, `conversation()` MAY have a default implementation returning null
- Implementations of `AgentContext` SHOULD provide a non-null Conversation

### Requirement: SimpleAgentContext

- MUST be a public class in `org.specdriven.agent.agent` implementing `AgentContext`
- MUST combine sessionId, config, toolRegistry, and conversation
- MUST accept all fields via constructor
- MUST return non-null conversation

### Requirement: LlmClient interface

- MUST define `chat(List<Message>)` returning `LlmResponse`
- MUST be a provider-agnostic abstraction — no provider-specific types in the interface
- MAY define additional methods for streaming or model selection in future changes

### Requirement: LlmResponse sealed interface

- MUST be a sealed interface in `org.specdriven.agent.agent` permitting exactly two subtypes: `TextResponse` and `ToolCallResponse`
- `TextResponse` MUST carry `content` (String) — the LLM's text reply
- `ToolCallResponse` MUST carry `toolCalls` (List<ToolCall>) — one or more tool invocations requested by the LLM; null MUST be treated as empty list
- Both subtypes MUST be Java records

### Requirement: ToolCall record

- MUST be a Java record in `org.specdriven.agent.agent` with fields: `toolName` (String), `parameters` (Map<String, Object>)
- MUST defensively copy the parameters map in the compact constructor; null MUST be treated as empty map

### Requirement: Orchestrator interface

- MUST define `run(AgentContext, LlmClient)` executing the full think-act-observe loop
- MUST be callable from `DefaultAgent.doExecute`

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
- Before invoking `tool.execute()`, MUST run `beforeExecute` on each registered `ToolExecutionHook` in list order
- If any hook's `beforeExecute` returns `ToolResult.Error`, MUST skip `tool.execute()` and use the hook's error as the result
- After successful tool execution, MUST run `afterExecute` on each registered hook
- Runs that terminate before any tool execution MUST NOT require successful permission policy store initialization
- MUST be able to suspend the current run when a structured question requiring deferred external input is raised
- While suspended, MUST NOT call `LlmClient.chat` again
- While suspended, MUST NOT execute additional tools
- MUST resume the same conversation after a matching answer is accepted
- MUST stop waiting and end the current run when the configured question wait timeout expires

#### Scenario: Null LLM returns without policy-store initialization
- GIVEN a `DefaultOrchestrator` with no tool execution to perform because `LlmClient` is null
- WHEN `run(AgentContext, LlmClient)` is called
- THEN it MUST return immediately
- AND it MUST NOT fail because permission policy storage was unavailable

#### Scenario: Tool-free response does not depend on policy-store initialization
- GIVEN a `DefaultOrchestrator` whose `LlmClient` returns a `TextResponse` without any tool calls
- WHEN `run(AgentContext, LlmClient)` is called
- THEN it MUST append the assistant text and stop normally
- AND it MUST NOT fail because permission policy storage was unavailable

#### Scenario: Pause prevents additional work
- GIVEN an orchestrator run that has entered question wait mode
- WHEN no answer has been accepted yet
- THEN the system MUST NOT append new assistant turns caused by extra LLM calls
- AND it MUST NOT append new tool results caused by extra tool execution

#### Scenario: Accepted answer resumes the same conversation
- GIVEN an orchestrator run paused on one waiting question
- WHEN a matching answer is accepted before timeout
- THEN the next LLM turn MUST continue from the same session conversation
- AND the accepted answer MUST be present in conversation history before that next turn

#### Scenario: Timeout ends the waiting run
- GIVEN an orchestrator run paused on one waiting question
- WHEN the configured wait timeout expires before any answer is accepted
- THEN the orchestrator MUST end the current wait
- AND it MUST return without executing additional LLM or tool turns for that run

### Requirement: OrchestratorConfig

- MUST be a Java record with `maxTurns` (int, default 50), `toolTimeoutSeconds` (int, default 120), `questionTimeoutSeconds` (int, default 300), and `hooks` (List<ToolExecutionHook>, default empty list)
- MUST provide a static factory `defaults()` returning the default configuration
- MUST provide a static factory `fromMap(Map<String, String>)` constructing config from key-value pairs with fallback to defaults
- MUST provide a convenience constructor without hooks for backward compatibility
- MUST be accepted by DefaultOrchestrator constructor

#### Scenario: Question timeout config comes from map
- GIVEN a config map containing `questionTimeoutSeconds`
- WHEN `OrchestratorConfig.fromMap(Map<String, String>)` is called
- THEN the returned config MUST expose that timeout value

### Requirement: DefaultAgent doExecute delegation

- `DefaultAgent.doExecute(AgentContext)` MUST create a DefaultOrchestrator and delegate execution
- MUST construct OrchestratorConfig from the agent's config map if present, otherwise use defaults
- MUST register a `PermissionCheckHook` in the OrchestratorConfig hooks list

### Requirement: Session record

- MUST be a Java record in `org.specdriven.agent.agent` with fields: `id` (String), `state` (AgentState), `createdAt` (long, epoch millis), `updatedAt` (long, epoch millis), `expiryAt` (long, epoch millis), `conversation` (Conversation)
- `id` MAY be null before first save; after save it MUST be a non-empty UUID string
- `expiryAt` MUST be set to `createdAt + 30 days` on construction and MUST NOT be modified by subsequent saves

### Requirement: SessionStore interface

- MUST be a public interface in `org.specdriven.agent.agent`
- MUST define `save(Session session)` returning String (the session ID); MUST generate a UUID if `session.id()` is null
- MUST define `load(String sessionId)` returning `Optional<Session>`
- MUST define `delete(String sessionId)` returning void
- MUST define `listActive()` returning `List<Session>` — all sessions whose `expiryAt` is in the future

### Requirement: LealoneSessionStore implementation

- MUST implement `SessionStore` in `org.specdriven.agent.agent`
- MUST persist sessions to two Lealone SQL tables: `agent_sessions` (structured columns) and `agent_messages` (one row per Message, content as JSON CLOB)
- MUST auto-create both tables on first initialization if they do not exist
- MUST serialize Message content using `com.lealone.orm.json.JsonObject`
- MUST start a background VirtualThread on initialization that deletes expired sessions and their messages every hour
- Background cleanup failures MUST be logged as warnings and MUST NOT propagate exceptions to callers

### Requirement: SimpleAgentContext SessionStore integration

- `SimpleAgentContext` MUST provide an additional constructor accepting `SessionStore` as an optional parameter
- The existing constructor without `SessionStore` MUST remain valid and unchanged in behavior
- When a `SessionStore` is present, `DefaultAgent.doExecute` MUST call `store.load(sessionId)` before invoking the orchestrator, and `store.save(session)` after the orchestrator completes or on exception

### Requirement: AgentContext.processManager()

- MUST add method `processManager()` to `AgentContext` interface
- MUST return `Optional<ProcessManager>` — empty if no process manager is available
- MUST have a default implementation returning `Optional.empty()` for backward compatibility

### Requirement: SimpleAgentContext ProcessManager support

- MUST add `ProcessManager` field to `SimpleAgentContext`
- MUST add constructor overload accepting `ProcessManager` as additional parameter
- MUST retain existing constructors for backward compatibility
- MUST implement `processManager()` returning `Optional.ofNullable(processManager)`

### Requirement: DefaultAgent background process cleanup on stop

- MUST call `cleanupBackgroundProcesses()` at the end of `stop()` method
- MUST perform cleanup AFTER state transition to STOPPED
- MUST NOT allow cleanup failures to prevent state transition

### Requirement: DefaultAgent background process cleanup on close

- MUST call `cleanupBackgroundProcesses()` at the end of `close()` method
- MUST act as a safety net for direct close() calls without prior stop()

### Requirement: DefaultAgent.cleanupBackgroundProcesses() helper

- MUST be a private method in `DefaultAgent`
- MUST check if the last `AgentContext` used in `doExecute()` has a `ProcessManager`
- MUST call `processManager.stopAll()` if ProcessManager is present
- MUST catch and swallow any exceptions from `stopAll()` (cleanup best-effort)
- MUST store reference to `AgentContext` during `doExecute()` for later cleanup access

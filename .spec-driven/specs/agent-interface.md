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

- MUST enforce the following valid transitions: IDLE→RUNNING (via start), RUNNING→STOPPED (via stop), RUNNING→PAUSED (reserved for orchestrator), RUNNING→ERROR (on uncaught exception in execute), PAUSED→RUNNING (reserved), PAUSED→STOPPED (via stop), ERROR→STOPPED (via stop)
- MUST reject any transition not listed above by throwing IllegalStateException with a descriptive message
- MUST treat STOPPED as a terminal state — no transition away from STOPPED is allowed

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

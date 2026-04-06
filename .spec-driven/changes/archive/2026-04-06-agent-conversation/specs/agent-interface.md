# Agent Interface Spec — Delta for agent-conversation

## ADDED Requirements

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

## UNCHANGED Requirements

(no changes to existing agent-interface.md requirements)

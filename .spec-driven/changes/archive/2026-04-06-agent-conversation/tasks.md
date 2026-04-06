# Tasks: agent-conversation

## Implementation

- [x] Define `Message` sealed interface with `role()`, `content()`, `timestamp()` in `org.specdriven.agent.agent`
- [x] Implement `UserMessage`, `AssistantMessage`, `ToolMessage`, `SystemMessage` as Java records implementing `Message`
- [x] Implement `Conversation` class with `append(Message)`, `get(int)`, `history()`, `size()`, `clear()` in `org.specdriven.agent.agent`
- [x] Add `conversation()` method to `AgentContext` interface (default returning null for backward compat)
- [x] Implement `SimpleAgentContext` record/class combining sessionId, config, toolRegistry, conversation

## Testing

- [x] `mvn test` passes — lint and validation
- [x] `MessageTest.java` — unit test each subtype returns correct role, content, and timestamp
- [x] Unit test ToolMessage additionally exposes toolName field
- [x] `ConversationTest.java` — unit test append, get, history, size, clear
- [x] Unit test Conversation.get throws IndexOutOfBoundsException for invalid index
- [x] Unit test Conversation.history returns unmodifiable list (mutation throws UnsupportedOperationException)
- [x] `SimpleAgentContextTest.java` — unit test all accessors return correct values
- [x] Unit test existing DefaultAgentTest still passes after AgentContext change

## Verification

- [x] Verify implementation matches proposal scope
- [x] Verify no existing behavior changed (run full test suite)

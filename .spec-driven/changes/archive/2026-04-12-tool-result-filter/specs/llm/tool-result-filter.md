---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/ToolResultFilter.java
    - src/main/java/org/specdriven/agent/agent/DefaultToolResultFilter.java
    - src/main/java/org/specdriven/agent/agent/ToolResultFilterInput.java
  tests:
    - src/test/java/org/specdriven/agent/agent/ToolResultFilterTest.java
---

# Tool Result Filter

## ADDED Requirements

### Requirement: ToolResultFilter contract
The system MUST provide a ToolResult filtering contract that can produce an optimized message list for LLM request preparation from explicit current-turn data and candidate conversation messages.

#### Scenario: Filter returns an optimized message list
- GIVEN a ToolResult filter
- AND a current turn with user-visible text and zero or more requested tool names
- AND a candidate message list containing prior tool and non-tool messages
- WHEN the caller asks the filter to optimize the messages
- THEN the filter MUST return a message list suitable for constructing an `LlmRequest`
- AND the returned list MUST be immutable from the caller's perspective

#### Scenario: Deterministic filtering for identical input
- GIVEN the same current-turn data, candidate messages, scorer, and retention policy
- WHEN the filter is invoked repeatedly
- THEN it MUST return equal message lists each time

### Requirement: Non-tool messages are preserved
The filter MUST preserve non-tool conversation messages in their original relative order.

#### Scenario: User and assistant messages remain
- GIVEN a candidate message list containing user, assistant, system, and tool messages
- WHEN the filter removes one or more irrelevant tool messages
- THEN the returned message list MUST still include every non-tool message
- AND those non-tool messages MUST remain in their original relative order

#### Scenario: Tool removal does not reorder retained messages
- GIVEN multiple retained messages around an irrelevant tool message
- WHEN the irrelevant tool message is removed
- THEN all remaining messages MUST appear in their original relative order

### Requirement: Relevant tool results are retained
The default filter MUST use `ContextRelevanceScorer` to retain prior tool result messages that are relevant to the current turn.

#### Scenario: Matching tool result remains
- GIVEN a prior tool message whose tool name or content is relevant to the current turn
- WHEN the default filter evaluates the candidate messages
- THEN that tool message MUST be retained in the returned message list

#### Scenario: Irrelevant ordinary tool result is removed
- GIVEN a prior tool message that is not mandatory to retain
- AND the relevance scorer classifies it as irrelevant to the current turn
- WHEN the default filter evaluates the candidate messages
- THEN that tool message MUST NOT appear in the returned message list

### Requirement: Mandatory retention takes precedence
The filter MUST preserve tool messages classified by `ContextRetentionPolicy` as `MANDATORY`, regardless of their current-turn relevance.

#### Scenario: Mandatory low-relevance tool result remains
- GIVEN a prior tool message with no relevance overlap with the current turn
- AND the retention policy classifies that message as `MANDATORY`
- WHEN the default filter evaluates the candidate messages
- THEN that tool message MUST be retained in the returned message list

#### Scenario: Active tool-call correlation remains
- GIVEN a prior tool message correlated with an active or unresolved tool call
- WHEN the default filter evaluates the candidate messages
- THEN that tool message MUST be retained even if the current turn does not mention its tool name or content

### Requirement: Explicit inputs and absent metadata handling
The filter input model MUST make current-turn text, requested tool names, candidate messages, and optional retention metadata observable without requiring direct access to orchestrator state.

#### Scenario: Filter without requested tool names
- GIVEN current-turn text with no requested tool names
- WHEN the filter evaluates candidate messages
- THEN relevance MUST still be computed from current-turn text

#### Scenario: Filter without current-turn text
- GIVEN an empty current-turn text
- AND one or more requested tool names
- WHEN the filter evaluates candidate messages
- THEN relevance MUST still be computed from requested tool names

#### Scenario: Missing optional retention metadata
- GIVEN a tool message with no explicit retention metadata
- WHEN the default filter evaluates that message
- THEN the filter MUST still classify it using observable tool message fields
- AND it MUST NOT throw because optional metadata is absent

### Requirement: LlmRequest helper preserves request parameters
If the system provides a helper for applying the filter to an existing `LlmRequest`, the helper MUST only replace the request message list and MUST preserve all other request parameters.

#### Scenario: Filtered request preserves generation parameters
- GIVEN an `LlmRequest` with system prompt, tools, temperature, max tokens, and extra parameters
- WHEN the filter helper creates a filtered request
- THEN the returned request MUST preserve the original system prompt, tools, temperature, max tokens, and extra parameters
- AND only the message list MAY differ

### Requirement: No implicit request-building behavior change
Adding the ToolResult filter MUST NOT change existing LLM request construction or provider behavior unless a caller explicitly invokes the filter.

#### Scenario: Existing LLM calls are unchanged
- GIVEN existing code that constructs an `LlmRequest`
- WHEN this change is applied
- THEN the request MUST contain the same messages, tool schemas, system prompt, and generation parameters as before unless the caller explicitly applies the ToolResult filter

---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/ConversationSummarizer.java
    - src/main/java/org/specdriven/agent/agent/DefaultConversationSummarizer.java
    - src/main/java/org/specdriven/agent/agent/ConversationSummary.java
    - src/main/java/org/specdriven/agent/agent/ConversationSummarizerInput.java
  tests:
    - src/test/java/org/specdriven/agent/agent/ConversationSummarizerTest.java
---

# Conversation Summarizer

## ADDED Requirements

### Requirement: ConversationSummarizer contract
The system MUST provide a Conversation summarization contract that can produce an optimized message list for LLM request preparation from explicit candidate messages and summarization limits.

#### Scenario: Summarizer returns an optimized message list
- GIVEN a Conversation summarizer
- AND candidate messages
- AND explicit recent-message and token-budget limits
- WHEN the caller asks the summarizer to optimize the messages
- THEN the summarizer MUST return a message list suitable for constructing an `LlmRequest`
- AND the returned list MUST be immutable from the caller's perspective

#### Scenario: Deterministic summarization for identical input
- GIVEN the same candidate messages, limits, and retention policy
- WHEN the summarizer is invoked repeatedly
- THEN it MUST return equal summarized message lists each time

### Requirement: Summary appears only when compression is needed
The default summarizer MUST leave the message list unchanged when the candidate messages fit within the configured limits.

#### Scenario: Messages under limit remain unchanged
- GIVEN candidate messages whose estimated token count is within the configured token budget
- WHEN the default summarizer evaluates the messages
- THEN the returned message list MUST equal the original candidate message list

#### Scenario: Empty input returns empty output
- GIVEN no candidate messages
- WHEN the default summarizer evaluates the input
- THEN it MUST return an empty immutable message list

### Requirement: Sliding recent-message window
The default summarizer MUST preserve the most recent configured number of messages without summarizing them.

#### Scenario: Recent messages remain complete
- GIVEN candidate messages that exceed the configured token budget
- AND a recent-message limit of N
- WHEN the default summarizer evaluates the messages
- THEN the last N candidate messages MUST appear in the returned message list with their original content

#### Scenario: Recent messages keep relative order
- GIVEN multiple recent messages are preserved
- WHEN the default summarizer returns the optimized message list
- THEN the preserved recent messages MUST appear in their original relative order

### Requirement: System messages are preserved
The default summarizer MUST preserve system messages without compressing their content.

#### Scenario: System messages remain complete
- GIVEN candidate messages that contain one or more system messages
- AND the messages exceed the configured token budget
- WHEN the default summarizer evaluates the messages
- THEN every system message MUST appear in the returned message list with its original content

#### Scenario: System messages appear before generated summary
- GIVEN system messages and older eligible history are both present
- WHEN the default summarizer creates a summary message
- THEN preserved system messages MUST appear before the generated summary message

### Requirement: Mandatory retention takes precedence over summarization
The summarizer MUST preserve messages classified by `ContextRetentionPolicy` as `MANDATORY`, regardless of age or token pressure.

#### Scenario: Mandatory older message remains complete
- GIVEN an older message outside the recent-message window
- AND the retention policy classifies the message as `MANDATORY`
- WHEN the default summarizer evaluates the messages
- THEN that message MUST appear in the returned message list with its original content

#### Scenario: Mandatory tool-call correlation remains complete
- GIVEN an older tool result correlated with an active or unresolved tool call
- WHEN the default summarizer evaluates the messages
- THEN that tool result MUST be retained even when other older tool results are summarized

### Requirement: Older eligible history is summarized
When compression is needed, older messages that are not system messages, not in the recent window, and not mandatory MUST be represented by a bounded summary instead of being silently dropped.

#### Scenario: Summary represents compressed messages
- GIVEN older eligible messages that exceed the configured budget
- WHEN the default summarizer evaluates the messages
- THEN the returned message list MUST contain a generated summary message
- AND the summary content MUST report how many messages were compressed
- AND the summary content MUST include observable role or tool context from the compressed messages

#### Scenario: Summary is bounded
- GIVEN a configured summary token budget
- WHEN the default summarizer creates a summary message
- THEN the estimated token count of the summary content MUST NOT exceed the configured summary token budget unless the configured budget is too small to represent required summary metadata

### Requirement: LlmRequest helper preserves request parameters
If the system provides a helper for applying summarization to an existing `LlmRequest`, the helper MUST only replace the request message list and MUST preserve all other request parameters.

#### Scenario: Summarized request preserves generation parameters
- GIVEN an `LlmRequest` with system prompt, tools, temperature, max tokens, and extra parameters
- WHEN the summarizer helper creates a summarized request
- THEN the returned request MUST preserve the original system prompt, tools, temperature, max tokens, and extra parameters
- AND only the message list MAY differ

### Requirement: No implicit request-building behavior change
Adding the Conversation summarizer MUST NOT change existing LLM request construction or provider behavior unless a caller explicitly invokes the summarizer.

#### Scenario: Existing LLM calls are unchanged
- GIVEN existing code that constructs an `LlmRequest`
- WHEN this change is applied
- THEN the request MUST contain the same messages, tool schemas, system prompt, and generation parameters as before unless the caller explicitly applies the summarizer

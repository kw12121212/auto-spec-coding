---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/SmartContextInjector.java
    - src/main/java/org/specdriven/agent/agent/SmartContextInjectorConfig.java
  tests:
    - src/test/java/org/specdriven/agent/agent/SmartContextInjectorTest.java
---

# Smart Context Injector

## ADDED Requirements

### Requirement: SmartContextInjector LlmClient decorator
The system MUST provide a smart context injector that can wrap an existing `LlmClient` and optimize outgoing conversation messages before the delegate client is called.

#### Scenario: Decorator delegates optimized list-based calls
- GIVEN a smart context injector wrapping a delegate LLM client
- AND a list of conversation messages eligible for context optimization
- WHEN `chat(List<Message>)` is called
- THEN the delegate MUST receive an optimized message list
- AND the delegate MUST be called exactly once
- AND the caller MUST receive the delegate response unchanged

#### Scenario: Decorator delegates optimized request-based calls
- GIVEN a smart context injector wrapping a delegate LLM client
- AND an `LlmRequest` eligible for context optimization
- WHEN `chat(LlmRequest)` is called
- THEN the delegate MUST receive an `LlmRequest` whose message list has been optimized
- AND the request system prompt, tools, temperature, max tokens, and extra parameters MUST be preserved
- AND the caller MUST receive the delegate response unchanged

#### Scenario: Plain clients remain unchanged
- GIVEN existing code that calls an unwrapped `LlmClient`
- WHEN the smart context injector exists in the system
- THEN the existing caller MUST send the same messages as before unless it explicitly uses the injector or an integration path that enables it

### Requirement: Optimization composition order
The smart context injector MUST compose the existing ToolResult filtering and Conversation summarization behaviors in a deterministic order.

#### Scenario: Tool results are filtered before summarization
- GIVEN an outgoing message list containing irrelevant non-mandatory tool results and older summarizable history
- WHEN the smart context injector optimizes the message list
- THEN irrelevant non-mandatory tool result messages MUST be removed before summarization is evaluated
- AND the summary MUST represent only the eligible messages that remain after filtering

#### Scenario: Deterministic optimized output
- GIVEN the same injector configuration, current-turn metadata, retention metadata, and outgoing messages
- WHEN the smart context injector optimizes the messages repeatedly
- THEN the delegate MUST receive equal optimized message lists each time

### Requirement: Mandatory context survives optimization
The smart context injector MUST preserve context classified as mandatory by `ContextRetentionPolicy` across both filtering and summarization.

#### Scenario: Recovery context remains available
- GIVEN a message classified as mandatory for recovery execution
- WHEN the smart context injector optimizes the outgoing messages
- THEN the delegate message list MUST contain that message with its original content

#### Scenario: Question and answer context remains available
- GIVEN messages classified as mandatory for question escalation or answer replay
- WHEN the smart context injector optimizes the outgoing messages
- THEN the delegate message list MUST contain those messages with their original content

#### Scenario: Audit and active tool-call context remains available
- GIVEN messages classified as mandatory for audit traceability or active tool-call correlation
- WHEN the smart context injector optimizes the outgoing messages
- THEN the delegate message list MUST contain those messages with their original content

### Requirement: Current-turn metadata handling
The smart context injector MUST make current-turn metadata observable to ToolResult filtering without requiring direct access to private orchestrator state.

#### Scenario: Explicit current-turn metadata is used
- GIVEN explicit current-turn text and requested tool names are provided to the injector
- WHEN the injector optimizes tool result messages
- THEN ToolResult relevance MUST be evaluated using the explicit current-turn text and requested tool names

#### Scenario: Missing current-turn metadata falls back safely
- GIVEN no explicit current-turn metadata is provided
- AND the outgoing messages contain one or more user-visible messages
- WHEN the injector optimizes tool result messages
- THEN the injector MUST use the latest user-visible message content as current-turn text
- AND it MUST use an empty requested-tool-name list

#### Scenario: No user-visible message is available
- GIVEN no explicit current-turn metadata is provided
- AND the outgoing messages contain no user-visible message
- WHEN the injector optimizes tool result messages
- THEN optimization MUST still complete without throwing because current-turn text is absent

### Requirement: Smart context configuration
The smart context injector MUST expose deterministic configuration for token budget, recent-message retention, summary budget, and enabled state.

#### Scenario: Disabled injector is transparent
- GIVEN a smart context injector whose configuration is disabled
- WHEN `chat(List<Message>)` or `chat(LlmRequest)` is called
- THEN the delegate MUST receive the original message list
- AND the caller MUST receive the delegate response unchanged

#### Scenario: Invalid budgets are rejected
- GIVEN a smart context configuration with a non-positive token budget, negative recent-message limit, or non-positive summary budget
- WHEN the configuration is created
- THEN the system MUST reject it with a descriptive runtime exception

#### Scenario: Valid budgets are applied
- GIVEN a smart context injector configured with token and summary budgets
- WHEN outgoing messages exceed the configured token budget
- THEN the summarization step MUST use the configured budgets to produce the optimized message list

### Requirement: Fixed context evaluation set
The system MUST provide a fixed local evaluation set for validating smart context optimization quality without live LLM calls.

#### Scenario: Token reduction threshold is met
- GIVEN the fixed context evaluation set
- WHEN baseline messages are optimized by the smart context injector
- THEN the optimized estimated token count MUST be at least 30 percent lower than the baseline estimated token count

#### Scenario: Critical context has no regression
- GIVEN the fixed context evaluation set includes recovery, question escalation, answer replay, audit traceability, and active tool-call markers
- WHEN baseline messages are optimized by the smart context injector
- THEN every marker required by the evaluation set MUST remain present in the optimized messages

#### Scenario: Evaluation report exposes comparison metrics
- GIVEN the fixed context evaluation set
- WHEN the evaluation is executed
- THEN the result MUST expose baseline estimated tokens, optimized estimated tokens, token reduction percentage, and critical-context preservation status

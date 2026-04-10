# Tasks: answer-agent-runtime

## Implementation

- [x] Create `AnswerAgentConfig` record with provider, model, temperature, maxTokens, timeoutSeconds, maxContextMessages
- [x] Create `AnswerAgentException` and `AnswerAgentTimeoutException` runtime exceptions
- [x] Create `AnswerAgent` interface with `resolve(Question, List<Message>)` method
- [x] Create `ContextWindowManager` class with `crop(List<Message>, Question)` method
  - [x] Implement recent message retention (default 10 messages)
  - [x] Preserve all system messages
  - [x] Implement fallback when cropped context is empty
- [x] Create `AnswerGenerationService` class
  - [x] Implement LLM client invocation with timeout
  - [x] Implement response parsing to extract answer content
  - [x] Implement structured `Answer` building with AI_AGENT source
- [x] Create `AnswerAgentRuntime` class
  - [x] Implement synchronous `resolve()` method
  - [x] Integrate ContextWindowManager for context cropping
  - [x] Integrate AnswerGenerationService for answer generation
  - [x] Handle errors and wrap in AnswerAgentException
- [x] Update `DefaultOrchestrator` to handle AUTO_AI_REPLY delivery mode
  - [x] Remove IllegalArgumentException for AUTO_AI_REPLY
  - [x] Add AnswerAgentRuntime integration path
  - [x] Ensure Answer is properly returned to conversation
- [x] Add AnswerAgent configuration to `OrchestratorConfig`
- [x] Update `SimpleAgentContext` to provide AnswerAgentRuntime when needed

## Testing

- [x] Unit test: `AnswerAgentConfig` validation
- [x] Unit test: `ContextWindowManager.crop()` with various conversation sizes
- [x] Unit test: `ContextWindowManager` preserves system messages
- [x] Unit test: `ContextWindowManager` fallback when context is empty
- [x] Unit test: `AnswerGenerationService` generates valid Answer structure
- [x] Unit test: `AnswerGenerationService` timeout handling
- [x] Unit test: `AnswerAgentRuntime.resolve()` success path
- [x] Unit test: `AnswerAgentRuntime` error handling and exception wrapping
- [x] Unit test: `DefaultOrchestrator` with AUTO_AI_REPLY (mock AnswerAgentRuntime)
- [x] Integration test: End-to-end AUTO_AI_REPLY flow with mock LLM
- [x] Run unit tests: `mvn test -Dtest="*AnswerAgent*"`
- [x] Run all tests: `mvn test`

## Verification

- [x] Verify AnswerAgentConfig has proper defaults
- [x] Verify ContextWindowManager crops context to maxContextMessages
- [x] Verify AnswerGenerationService sets correct Answer fields:
  - [x] content (non-empty)
  - [x] basisSummary (non-empty)
  - [x] sourceRef (non-empty)
  - [x] source = AI_AGENT
  - [x] confidence = 0.9
  - [x] decision = ANSWER_ACCEPTED
  - [x] deliveryMode matches question
  - [x] answeredAt is valid timestamp
- [x] Verify AUTO_AI_REPLY does not transition agent to PAUSED state
- [x] Verify Answer is appended to conversation as SystemMessage
- [x] Verify audit events are emitted (QUESTION_CREATED, QUESTION_ANSWERED)
- [x] Verify error cases throw AnswerAgentException or AnswerAgentTimeoutException

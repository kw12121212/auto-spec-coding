# question-resolution.md - Delta Spec for answer-agent-runtime

## ADDED Requirements

### Requirement: AnswerAgent interface

The system MUST provide an `AnswerAgent` interface for generating answers to questions automatically.

#### Scenario: AnswerAgent resolves a question
- GIVEN a `Question` with `deliveryMode == AUTO_AI_REPLY`
- AND a conversation history
- WHEN `AnswerAgent.resolve(question, messages)` is called
- THEN it MUST return a valid `Answer` instance
- AND the `Answer.source` MUST be `AI_AGENT`
- AND the `Answer.deliveryMode` MUST match the question's delivery mode
- AND the `Answer.decision` MUST be `ANSWER_ACCEPTED`

#### Scenario: AnswerAgent uses cropped context
- GIVEN a conversation with many messages
- WHEN `AnswerAgent` generates an answer
- THEN it MUST use a cropped subset of the conversation
- AND the cropped context MUST include the most recent messages
- AND the cropped context MUST include all system messages

### Requirement: AnswerAgentConfig

The system MUST provide `AnswerAgentConfig` for configuring the Answer Agent behavior.

#### Scenario: Config has required fields
- GIVEN an `AnswerAgentConfig` instance
- THEN it MUST expose `provider` (String)
- AND it MUST expose `model` (String)
- AND it MUST expose `temperature` (double, default 0.3)
- AND it MUST expose `maxTokens` (int, default 1024)
- AND it MUST expose `timeoutSeconds` (long, default 30)
- AND it MUST expose `maxContextMessages` (int, default 10)

#### Scenario: Temperature validation
- GIVEN a config with temperature outside [0.0, 2.0]
- WHEN the config is constructed
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: Positive timeout and tokens
- GIVEN a config with non-positive `timeoutSeconds` or `maxTokens`
- WHEN the config is constructed
- THEN it MUST throw `IllegalArgumentException`

### Requirement: ContextWindowManager

The system MUST provide `ContextWindowManager` for cropping conversation context.

#### Scenario: Crop retains recent messages
- GIVEN a conversation with more than `maxContextMessages` messages
- WHEN `crop(messages, question)` is called
- THEN it MUST return at most `maxContextMessages` messages
- AND the returned list MUST contain the most recent messages

#### Scenario: Crop preserves system messages
- GIVEN a conversation with system messages
- WHEN the context is cropped
- THEN all system messages MUST be preserved in the result
- AND they MUST appear at the beginning of the cropped list

#### Scenario: Crop fallback for empty result
- GIVEN a conversation where all messages would be filtered out
- WHEN `crop(messages, question)` is called
- THEN it MUST return a fallback context containing at least the question payload

### Requirement: AnswerAgentRuntime

The system MUST provide `AnswerAgentRuntime` as the main entry point for Answer Agent resolution.

#### Scenario: Runtime resolves question synchronously
- GIVEN a `Question` with `AUTO_AI_REPLY` delivery mode
- AND a valid conversation history
- WHEN `AnswerAgentRuntime.resolve(question, conversation)` is called
- THEN it MUST return an `Answer` without blocking for external input
- AND it MUST NOT transition the agent to `PAUSED` state

#### Scenario: Runtime emits lifecycle events
- GIVEN a successful answer resolution
- WHEN the resolution completes
- THEN a `QUESTION_CREATED` event MUST be emitted
- AND a `QUESTION_ANSWERED` event MUST be emitted

#### Scenario: Runtime handles timeout
- GIVEN an LLM call that exceeds timeout
- WHEN `resolve()` is called
- THEN it MUST throw `AnswerAgentTimeoutException`

#### Scenario: Runtime handles LLM errors
- GIVEN an LLM call that fails
- WHEN `resolve()` is called
- THEN it MUST throw `AnswerAgentException` with the cause

### Requirement: AnswerGenerationService

The system MUST provide `AnswerGenerationService` for generating structured answers via LLM.

#### Scenario: Service generates valid Answer structure
- GIVEN a cropped context and a question
- WHEN `generate(context, question)` is called
- THEN it MUST return an `Answer` with:
  - non-empty `content`
  - non-empty `basisSummary`
  - non-empty `sourceRef`
  - `source == AI_AGENT`
  - `confidence == 0.9`
  - `decision == ANSWER_ACCEPTED`
  - `deliveryMode` matching the question
  - valid `answeredAt` timestamp

#### Scenario: Service uses configured LLM provider
- GIVEN an `AnswerAgentConfig` with specific provider and model
- WHEN answer generation is requested
- THEN it MUST use the configured provider
- AND it MUST use the configured model

## CHANGED Requirements

### Requirement: DefaultOrchestrator handles AUTO_AI_REPLY

#### Scenario: AUTO_AI_REPLY triggers AnswerAgentRuntime (CHANGED)
- GIVEN a question tool call with `deliveryMode == AUTO_AI_REPLY`
- WHEN `handleQuestionToolCall()` processes it
- THEN it MUST invoke `AnswerAgentRuntime.resolve()`
- AND it MUST append the returned `Answer` to the conversation as a `SystemMessage`
- AND it MUST NOT throw `IllegalArgumentException`
- AND it MUST NOT transition the agent to `PAUSED` state
- AND it MUST continue the orchestrator loop

### Requirement: OrchestratorConfig includes AnswerAgent configuration

#### Scenario: Config provides AnswerAgent settings (CHANGED)
- GIVEN an `OrchestratorConfig` instance
- THEN it MUST expose `answerAgentConfig()` returning `Optional<AnswerAgentConfig>`

---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/answer/AnswerAgent.java
    - src/main/java/org/specdriven/agent/answer/AnswerAgentConfig.java
    - src/main/java/org/specdriven/agent/answer/AnswerAgentException.java
    - src/main/java/org/specdriven/agent/answer/AnswerAgentRuntime.java
    - src/main/java/org/specdriven/agent/answer/AnswerAgentTimeoutException.java
    - src/main/java/org/specdriven/agent/answer/AnswerContextManager.java
    - src/main/java/org/specdriven/agent/answer/AnswerGenerationService.java
    - src/main/java/org/specdriven/agent/agent/ContextWindowManager.java
    - src/main/java/org/specdriven/agent/http/AuthFilter.java
    - src/main/java/org/specdriven/agent/http/HttpApiServlet.java
    - src/main/java/org/specdriven/agent/http/RateLimitFilter.java
    - src/main/java/org/specdriven/agent/question/Answer.java
    - src/main/java/org/specdriven/agent/question/AnswerSource.java
    - src/main/java/org/specdriven/agent/question/BuiltinMobileAdapters.java
    - src/main/java/org/specdriven/agent/question/DefaultMaskingStrategy.java
    - src/main/java/org/specdriven/agent/question/DeliveryAttempt.java
    - src/main/java/org/specdriven/agent/question/DeliveryLogModel.java
    - src/main/java/org/specdriven/agent/question/DeliveryLogStore.java
    - src/main/java/org/specdriven/agent/question/DeliveryMode.java
    - src/main/java/org/specdriven/agent/question/DeliveryStatus.java
    - src/main/java/org/specdriven/agent/question/DiscordChannelProvider.java
    - src/main/java/org/specdriven/agent/question/DiscordDeliveryChannel.java
    - src/main/java/org/specdriven/agent/question/DiscordMessageTemplate.java
    - src/main/java/org/specdriven/agent/question/DiscordReplyCollector.java
    - src/main/java/org/specdriven/agent/question/InMemoryReplyCollector.java
    - src/main/java/org/specdriven/agent/question/LealoneDeliveryLogStore.java
    - src/main/java/org/specdriven/agent/question/LealoneQuestionStore.java
    - src/main/java/org/specdriven/agent/question/LoggingDeliveryChannel.java
    - src/main/java/org/specdriven/agent/question/MaskingStrategy.java
    - src/main/java/org/specdriven/agent/question/MobileAdapterException.java
    - src/main/java/org/specdriven/agent/question/MobileChannelConfig.java
    - src/main/java/org/specdriven/agent/question/MobileChannelHandle.java
    - src/main/java/org/specdriven/agent/question/MobileChannelProvider.java
    - src/main/java/org/specdriven/agent/question/MobileChannelRegistry.java
    - src/main/java/org/specdriven/agent/question/PlainTextFormatter.java
    - src/main/java/org/specdriven/agent/question/Question.java
    - src/main/java/org/specdriven/agent/question/QuestionCategory.java
    - src/main/java/org/specdriven/agent/question/QuestionDecision.java
    - src/main/java/org/specdriven/agent/question/QuestionDeliveryChannel.java
    - src/main/java/org/specdriven/agent/question/QuestionDeliveryService.java
    - src/main/java/org/specdriven/agent/question/QuestionEvents.java
    - src/main/java/org/specdriven/agent/question/QuestionMessageTemplate.java
    - src/main/java/org/specdriven/agent/question/QuestionReplyCollector.java
    - src/main/java/org/specdriven/agent/question/QuestionRoutingPolicy.java
    - src/main/java/org/specdriven/agent/question/QuestionRuntime.java
    - src/main/java/org/specdriven/agent/question/QuestionStatus.java
    - src/main/java/org/specdriven/agent/question/QuestionStore.java
    - src/main/java/org/specdriven/agent/question/ReplyCallbackRouter.java
    - src/main/java/org/specdriven/agent/question/RetryConfig.java
    - src/main/java/org/specdriven/agent/question/RetryingDeliveryChannel.java
    - src/main/java/org/specdriven/agent/question/RichMessageFormatter.java
    - src/main/java/org/specdriven/agent/question/TelegramChannelProvider.java
    - src/main/java/org/specdriven/agent/question/TelegramDeliveryChannel.java
    - src/main/java/org/specdriven/agent/question/TelegramMessageTemplate.java
    - src/main/java/org/specdriven/agent/question/TelegramReplyCollector.java
    - src/main/java/org/specdriven/agent/interactive/InteractiveSession.java
    - src/main/java/org/specdriven/agent/question/TemplateFieldPolicy.java
  tests:
    - src/test/java/org/specdriven/agent/answer/AnswerAgentConfigTest.java
    - src/test/java/org/specdriven/agent/answer/AnswerAgentRuntimeTest.java
    - src/test/java/org/specdriven/agent/answer/AnswerContextManagerTest.java
    - src/test/java/org/specdriven/agent/http/HttpCallbackEndpointTest.java
    - src/test/java/org/specdriven/agent/question/AnswerTest.java
    - src/test/java/org/specdriven/agent/question/BuiltinMobileAdaptersTest.java
    - src/test/java/org/specdriven/agent/question/DefaultMaskingStrategyTest.java
    - src/test/java/org/specdriven/agent/question/DeliveryAttemptTest.java
    - src/test/java/org/specdriven/agent/question/DeliveryStatusTest.java
    - src/test/java/org/specdriven/agent/question/DiscordChannelProviderTest.java
    - src/test/java/org/specdriven/agent/question/DiscordDeliveryChannelTemplateTest.java
    - src/test/java/org/specdriven/agent/question/DiscordDeliveryChannelTest.java
    - src/test/java/org/specdriven/agent/question/DiscordMessageTemplateTest.java
    - src/test/java/org/specdriven/agent/question/DiscordReplyCollectorTest.java
    - src/test/java/org/specdriven/agent/question/InMemoryReplyCollectorTest.java
    - src/test/java/org/specdriven/agent/question/LealoneDeliveryLogStoreTest.java
    - src/test/java/org/specdriven/agent/question/LealoneQuestionStoreTest.java
    - src/test/java/org/specdriven/agent/question/LoggingDeliveryChannelTest.java
    - src/test/java/org/specdriven/agent/question/MobileAdapterExceptionTest.java
    - src/test/java/org/specdriven/agent/question/MobileChannelConfigTest.java
    - src/test/java/org/specdriven/agent/question/MobileChannelHandleTest.java
    - src/test/java/org/specdriven/agent/question/MobileChannelRegistryTest.java
    - src/test/java/org/specdriven/agent/question/PlainTextFormatterTest.java
    - src/test/java/org/specdriven/agent/question/QuestionDeliveryServiceTest.java
    - src/test/java/org/specdriven/agent/question/QuestionEventsTest.java
    - src/test/java/org/specdriven/agent/question/QuestionMessageTemplateTest.java
    - src/test/java/org/specdriven/agent/question/QuestionRoutingPolicyTest.java
    - src/test/java/org/specdriven/agent/question/QuestionRuntimeTest.java
    - src/test/java/org/specdriven/agent/question/QuestionTest.java
    - src/test/java/org/specdriven/agent/question/ReplyCallbackRouterTest.java
    - src/test/java/org/specdriven/agent/question/RetryConfigTest.java
    - src/test/java/org/specdriven/agent/question/RetryingDeliveryChannelTest.java
    - src/test/java/org/specdriven/agent/question/TelegramChannelProviderTest.java
    - src/test/java/org/specdriven/agent/question/TelegramDeliveryChannelTemplateTest.java
    - src/test/java/org/specdriven/agent/question/TelegramDeliveryChannelTest.java
    - src/test/java/org/specdriven/agent/question/TelegramMessageTemplateTest.java
    - src/test/java/org/specdriven/agent/question/TelegramReplyCollectorTest.java
    - src/test/java/org/specdriven/agent/interactive/InteractiveSessionTest.java
    - src/test/java/org/specdriven/agent/question/TemplateFieldPolicyTest.java
---

# Question Resolution Spec

## ADDED Requirements

### Requirement: Question contract

The system MUST define a `Question` type representing a structured question raised during agent execution.

#### Scenario: Question contains required payload fields
- GIVEN a `Question` instance
- THEN it MUST expose non-empty `question`, `impact`, and `recommendation` fields

#### Scenario: Question identifies its session and status
- GIVEN a `Question` instance created for an agent session
- THEN it MUST expose a stable `questionId`
- AND it MUST expose the originating `sessionId`
- AND it MUST expose a `QuestionStatus`

#### Scenario: Question records delivery mode
- GIVEN a `Question` instance
- THEN it MUST expose a `DeliveryMode` indicating how the system expects the question to be answered

#### Scenario: Question exposes canonical payload map
- GIVEN a `Question` instance
- WHEN its structured payload is requested
- THEN the payload MUST include `questionId`, `sessionId`, `question`, `impact`, `recommendation`, `status`, and `deliveryMode`
- AND `status` and `deliveryMode` MUST be serialized as enum names

### Requirement: DeliveryMode enum

The system MUST define a `DeliveryMode` enum for the supported reply paths.

#### Scenario: Required delivery modes
- THEN `DeliveryMode` MUST include `AUTO_AI_REPLY`
- AND `DeliveryMode` MUST include `PUSH_MOBILE_WAIT_HUMAN`
- AND `DeliveryMode` MUST include `PAUSE_WAIT_HUMAN`

### Requirement: QuestionStatus enum

The system MUST define a `QuestionStatus` enum describing the observable lifecycle of a question.

#### Scenario: Required statuses
- THEN `QuestionStatus` MUST include `OPEN`
- AND `QuestionStatus` MUST include `WAITING_FOR_ANSWER`
- AND `QuestionStatus` MUST include `ANSWERED`
- AND `QuestionStatus` MUST include `ESCALATED`
- AND `QuestionStatus` MUST include `EXPIRED`
- AND `QuestionStatus` MUST include `CLOSED`

### Requirement: Answer contract

The system MUST define an `Answer` type representing an AI or human response to a `Question`.

#### Scenario: Answer contains required attribution
- GIVEN an `Answer` instance
- THEN it MUST expose non-empty `content`, `basisSummary`, and `sourceRef` fields
- AND it MUST expose an `AnswerSource`
- AND it MUST expose a numeric `confidence`
- AND it MUST expose a `QuestionDecision`
- AND it MUST expose a `DeliveryMode`
- AND it MUST expose `answeredAt`

#### Scenario: Confidence is bounded
- GIVEN an `Answer` instance
- WHEN `confidence` is inspected
- THEN it MUST be in the inclusive range `[0.0, 1.0]`

#### Scenario: Escalated answer requires escalation reason
- GIVEN an `Answer` instance for a question whose final decision is escalated
- THEN `escalationReason` MUST be non-empty

#### Scenario: Human-routed answer requires escalation reason
- GIVEN an `Answer` instance resolved through `PUSH_MOBILE_WAIT_HUMAN` or `PAUSE_WAIT_HUMAN`
- THEN `escalationReason` MUST be non-empty

#### Scenario: Auto AI answer may omit escalation reason
- GIVEN an `Answer` instance for a normally answered question using `AUTO_AI_REPLY`
- THEN `escalationReason` MAY be null or empty

#### Scenario: Answer exposes canonical audit metadata
- GIVEN an `Answer` instance
- WHEN its audit metadata is requested
- THEN the metadata MUST include `source`, `basisSummary`, `confidence`, `sourceRef`, `decision`, `deliveryMode`, and `answeredAt`
- AND `source`, `decision`, and `deliveryMode` MUST be serialized as enum names
- AND `escalationReason` MUST be present when required by the answer

### Requirement: AnswerSource enum

The system MUST define an `AnswerSource` enum identifying how the answer was produced.

#### Scenario: Required answer sources
- THEN `AnswerSource` MUST include `AI_AGENT`
- AND `AnswerSource` MUST include `HUMAN_MOBILE`
- AND `AnswerSource` MUST include `HUMAN_INLINE`

### Requirement: QuestionDecision enum

The system MUST define a `QuestionDecision` enum describing the observable outcome of question handling.

#### Scenario: Required decisions
- THEN `QuestionDecision` MUST include `ANSWER_ACCEPTED`
- AND `QuestionDecision` MUST include `ESCALATE_TO_HUMAN`
- AND `QuestionDecision` MUST include `TIMEOUT`
- AND `QuestionDecision` MUST include `CANCELLED`

### Requirement: Question audit fields

The system MUST persist or emit enough metadata to explain how each question was handled.

#### Scenario: Audit fields for answered question
- GIVEN a question that receives an answer
- THEN the audit record MUST include `questionId`, `sessionId`, `deliveryMode`, `status`, `decision`, and `answeredAt`
- AND it MUST include the answer `source`, `basisSummary`, `confidence`, and `sourceRef`

#### Scenario: Audit fields for escalated question
- GIVEN a question that is escalated to human handling
- THEN the audit record MUST include a non-empty `escalationReason`
- AND it MUST preserve the latest `deliveryMode` and `QuestionStatus`

### Requirement: Question lifecycle events

The system MUST standardize event names for question lifecycle visibility.

#### Scenario: Required question lifecycle events
- THEN the event model MUST include `QUESTION_CREATED`
- AND it MUST include `QUESTION_ANSWERED`
- AND it MUST include `QUESTION_ESCALATED`
- AND it MUST include `QUESTION_EXPIRED`

#### Scenario: Question-created event metadata
- GIVEN a `QUESTION_CREATED` event
- THEN its metadata MUST include `questionId`, `sessionId`, `deliveryMode`, and `status`

#### Scenario: Question-answered event metadata
- GIVEN a `QUESTION_ANSWERED` event
- THEN its metadata MUST include `questionId`, `sessionId`, `decision`, `source`, and `confidence`

#### Scenario: Question-escalated event metadata
- GIVEN a `QUESTION_ESCALATED` event
- THEN its metadata MUST include `questionId`, `sessionId`, `deliveryMode`, and `escalationReason`

### Requirement: QuestionEvents utility

The system MUST provide a `QuestionEvents` utility for constructing standard question lifecycle events.

#### Scenario: Build question-created event
- GIVEN a `Question` instance
- WHEN a created event is requested
- THEN the result MUST be an `Event` with type `QUESTION_CREATED`

#### Scenario: Build question-answered event
- GIVEN a `Question` instance and an `Answer` instance with the same `deliveryMode`
- WHEN an answered event is requested
- THEN the result MUST be an `Event` with type `QUESTION_ANSWERED`

#### Scenario: Reject mismatched delivery modes
- GIVEN a `Question` instance and an `Answer` instance with different `deliveryMode` values
- WHEN a question-answered or question-escalated event is requested
- THEN the system MUST reject the request

### Requirement: Waiting question lifecycle

The system MUST define how a structured question enters and leaves the waiting state during agent execution.

#### Scenario: Human-wait question enters waiting state
- GIVEN a running agent raises a structured question with delivery mode `PAUSE_WAIT_HUMAN` or `PUSH_MOBILE_WAIT_HUMAN`
- WHEN the orchestrator hands control over to external answer handling
- THEN the question MUST first be created with status `OPEN`
- AND it MUST transition to `WAITING_FOR_ANSWER` before the run blocks for an answer

#### Scenario: Waiting question emits creation visibility
- GIVEN a structured question that enters waiting state
- WHEN the waiting phase begins
- THEN the system MUST emit `QUESTION_CREATED`
- AND the event metadata MUST identify the waiting question and its session

### Requirement: Single waiting question per session

The system MUST limit the first implementation to one unresolved waiting question per session.

#### Scenario: Reject second waiting question
- GIVEN a session that already has one question in `WAITING_FOR_ANSWER`
- WHEN the same session attempts to create another waiting question
- THEN the system MUST reject the second question
- AND the original waiting question MUST remain the only unresolved question for that session

### Requirement: Waiting answer acceptance

The system MUST define the observable rules for accepting an answer to a waiting question.

#### Scenario: Matching answer resolves waiting question
- GIVEN a question in `WAITING_FOR_ANSWER`
- WHEN the system receives an answer whose `questionId`, `sessionId`, and `deliveryMode` match that waiting question
- THEN the question status MUST transition to `ANSWERED`
- AND the system MUST emit `QUESTION_ANSWERED`
- AND the answer content and audit context MUST be written to the session conversation before execution resumes

#### Scenario: Mismatched answer is rejected
- GIVEN a question in `WAITING_FOR_ANSWER`
- WHEN an answer is submitted for a different question, session, or delivery mode
- THEN the system MUST reject the answer
- AND the waiting question MUST remain unresolved

### Requirement: Waiting question timeout

The system MUST define the observable timeout behavior for a waiting question.

#### Scenario: Waiting question expires on timeout
- GIVEN a question in `WAITING_FOR_ANSWER`
- WHEN the configured wait timeout elapses before any answer is accepted
- THEN the question status MUST transition to `EXPIRED`
- AND the system MUST emit `QUESTION_EXPIRED`

#### Scenario: Late answer is rejected after expiry
- GIVEN a question whose status is `EXPIRED`
- WHEN a later answer is submitted for that question
- THEN the system MUST reject the answer
- AND the question status MUST remain `EXPIRED`

### Requirement: Waiting question cleanup on stop

The system MUST clear waiting-question runtime state when the paused run is stopped before any answer is accepted.

#### Scenario: Stop closes waiting question
- GIVEN a question in `WAITING_FOR_ANSWER`
- WHEN the paused run is stopped or closed before any answer is accepted
- THEN the system MUST end the wait for that question
- AND any later answer for that question MUST be rejected

### Requirement: Question category model

The system MUST classify structured questions into observable categories before routing them for an answer.

#### Scenario: Required question categories
- THEN the system MUST define `QuestionCategory.CLARIFICATION`
- AND it MUST define `QuestionCategory.PLAN_SELECTION`
- AND it MUST define `QuestionCategory.PERMISSION_CONFIRMATION`
- AND it MUST define `QuestionCategory.IRREVERSIBLE_APPROVAL`

#### Scenario: Question exposes category
- GIVEN a `Question` instance created for routing
- THEN it MUST expose a `QuestionCategory`
- AND the category MUST be included in the question's canonical structured payload as an enum name

### Requirement: Default routing policy

The system MUST define a default routing policy from `QuestionCategory` to `DeliveryMode`.

#### Scenario: Clarification defaults to auto AI reply
- GIVEN a structured question categorized as `CLARIFICATION`
- WHEN the default routing policy is applied
- THEN the selected `DeliveryMode` MUST be `AUTO_AI_REPLY`

#### Scenario: Plan selection defaults to auto AI reply
- GIVEN a structured question categorized as `PLAN_SELECTION`
- WHEN the default routing policy is applied
- THEN the selected `DeliveryMode` MUST be `AUTO_AI_REPLY`

#### Scenario: Permission confirmation defaults to human handling
- GIVEN a structured question categorized as `PERMISSION_CONFIRMATION`
- WHEN the default routing policy is applied
- THEN the selected `DeliveryMode` MUST NOT be `AUTO_AI_REPLY`
- AND the selected `DeliveryMode` MUST be `PAUSE_WAIT_HUMAN`

#### Scenario: Irreversible approval defaults to human handling
- GIVEN a structured question categorized as `IRREVERSIBLE_APPROVAL`
- WHEN the default routing policy is applied
- THEN the selected `DeliveryMode` MUST NOT be `AUTO_AI_REPLY`
- AND the selected `DeliveryMode` MUST be `PAUSE_WAIT_HUMAN`

### Requirement: Human-only escalation policy

The system MUST prevent human-only question categories from being auto-answered.

#### Scenario: Permission confirmation cannot be auto-answered
- GIVEN a structured question categorized as `PERMISSION_CONFIRMATION`
- WHEN an auto-answer route is requested
- THEN the system MUST reject `AUTO_AI_REPLY` for that question
- AND it MUST preserve a human-handled delivery mode for the unresolved question

#### Scenario: Irreversible approval cannot be auto-answered
- GIVEN a structured question categorized as `IRREVERSIBLE_APPROVAL`
- WHEN an auto-answer route is requested
- THEN the system MUST reject `AUTO_AI_REPLY` for that question
- AND it MUST preserve a human-handled delivery mode for the unresolved question

#### Scenario: Loop can observe human-only categories
- GIVEN a structured question categorized as `PERMISSION_CONFIRMATION` or `IRREVERSIBLE_APPROVAL`
- WHEN the autonomous loop inspects the question
- THEN the question MUST be observable as requiring human handling
- AND its routing metadata MUST include a non-empty `routingReason`
- AND the routing metadata MUST be usable by loop escalation events without requiring a second routing decision

### Requirement: Routing decision auditability

The system MUST make the routing decision observable before answer execution begins.

#### Scenario: Question payload includes routing basis
- GIVEN a structured question prepared for routing
- WHEN its canonical structured payload is requested
- THEN the payload MUST include `category`
- AND it MUST include `deliveryMode`

#### Scenario: Audit metadata records routed category
- GIVEN a question that has been routed for answer handling
- THEN the audit record or emitted metadata MUST include the routed `category`
- AND it MUST include the selected `deliveryMode`

#### Scenario: Escalated human-only route records why AI was not used
- GIVEN a question categorized as `PERMISSION_CONFIRMATION` or `IRREVERSIBLE_APPROVAL`
- WHEN the question is routed for human handling
- THEN the routing metadata MUST explain that the category requires human approval

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

### Requirement: QuestionDeliveryChannel

The system MUST define a `QuestionDeliveryChannel` interface for pushing question notifications to external channels.

#### Scenario: Send question to channel
- GIVEN a `Question` with status `WAITING_FOR_ANSWER`
- WHEN `send(question)` is called
- THEN the implementation MUST accept the question without throwing
- AND the question payload MUST be available for external consumption

#### Scenario: Channel lifecycle
- GIVEN a `QuestionDeliveryChannel` instance
- WHEN `close()` is called
- THEN the channel MUST release any held resources

### Requirement: LoggingDeliveryChannel

The system MUST provide a `LoggingDeliveryChannel` as the default `QuestionDeliveryChannel` implementation.

#### Scenario: Log question payload
- GIVEN a `Question` with status `WAITING_FOR_ANSWER`
- WHEN `send(question)` is called on a `LoggingDeliveryChannel`
- THEN the question payload MUST be written to the system logger

### Requirement: QuestionReplyCollector

The system MUST define a `QuestionReplyCollector` interface for receiving and validating external human replies.

#### Scenario: Collect valid reply
- GIVEN a `Question` with status `WAITING_FOR_ANSWER`
- AND a valid `Answer` with matching `questionId`, `sessionId`, and `deliveryMode`
- WHEN `collect(sessionId, questionId, answer)` is called
- THEN the answer MUST be accepted without throwing

#### Scenario: Reject mismatched reply
- GIVEN a `Question` with status `WAITING_FOR_ANSWER`
- AND an `Answer` with mismatched `questionId` or `deliveryMode`
- WHEN `collect(sessionId, questionId, answer)` is called
- THEN the system MUST throw `IllegalArgumentException`

#### Scenario: Reject reply for non-waiting question
- GIVEN no question in `WAITING_FOR_ANSWER` for the given session
- WHEN `collect(sessionId, questionId, answer)` is called
- THEN the system MUST throw `IllegalStateException`

#### Scenario: Collector lifecycle
- GIVEN a `QuestionReplyCollector` instance
- WHEN `close()` is called
- THEN the collector MUST release any held resources

### Requirement: InMemoryReplyCollector

The system MUST provide an `InMemoryReplyCollector` as the default `QuestionReplyCollector` implementation.

#### Scenario: Collect and forward to runtime
- GIVEN a `QuestionRuntime` and an `InMemoryReplyCollector`
- WHEN a valid answer is collected
- THEN the answer MUST be forwarded to `QuestionRuntime.submitAnswer()`

### Requirement: QuestionDeliveryService

The system MUST provide a `QuestionDeliveryService` facade that combines delivery and reply collection.

#### Scenario: Deliver pushes to channel
- GIVEN a `QuestionDeliveryService` with a configured channel
- WHEN `deliver(question)` is called
- THEN the question MUST be sent to the configured `QuestionDeliveryChannel`

#### Scenario: Submit reply via service
- GIVEN a waiting question and a valid answer
- WHEN `submitReply(sessionId, questionId, answer)` is called
- THEN the answer MUST be validated and forwarded to the `QuestionReplyCollector`
- AND the answer MUST be forwarded to `QuestionRuntime.submitAnswer()`

#### Scenario: Service delegates storage
- GIVEN a `QuestionDeliveryService` with a configured `QuestionStore`
- WHEN a question is delivered or answered
- THEN the state change MUST be persisted via the `QuestionStore`

#### Scenario: Push-mobile loop escalation reaches delivery channel
- GIVEN an escalated loop question with delivery mode `PUSH_MOBILE_WAIT_HUMAN`
- AND a configured question delivery surface
- WHEN the loop escalates the question
- THEN the question MUST be available to the configured mobile delivery channel

#### Scenario: Pause-wait loop escalation remains pending
- GIVEN an escalated loop question with delivery mode `PAUSE_WAIT_HUMAN`
- AND a configured question delivery surface
- WHEN the loop escalates the question
- THEN the question MUST be available as a pending waiting question for the session

#### Scenario: Loop escalation remains observable without delivery surface
- GIVEN an escalated loop question
- AND no question delivery surface is configured for the loop
- WHEN the loop escalates the question
- THEN the question MUST remain observable through loop escalation event metadata

#### Scenario: Loop escalation delivery failure is not successful
- GIVEN an escalated loop question
- AND the configured delivery surface fails to deliver it
- WHEN the loop handles the failure
- THEN the loop iteration MUST NOT be marked successful
- AND the loop MUST be left in `PAUSED` or `ERROR`
- AND the loop MUST NOT continue in `RUNNING`

### Requirement: Loop escalation audit context

The system MUST expose enough audit context for every loop-escalated question.

#### Scenario: Escalated loop question preserves routing context
- GIVEN a loop question escalated to human handling
- THEN the audit context MUST identify the originating session
- AND it MUST identify the question category
- AND it MUST identify the delivery mode
- AND it MUST explain why AI auto-answering was not used
- AND it MUST preserve the original `questionId`, `sessionId`, `category`, `deliveryMode`, and `routingReason`

### Requirement: QuestionStore

The system MUST define a `QuestionStore` interface for persistent question storage.

#### Scenario: Save new question
- GIVEN a `Question` instance
- WHEN `save(question)` is called
- THEN the question MUST be persisted with all fields
- AND the method MUST return the `questionId`

#### Scenario: Update question status
- GIVEN a persisted question
- WHEN `update(questionId, status)` is called
- THEN the question's status MUST be updated
- AND `updatedAt` MUST reflect the current time

#### Scenario: Find by session
- GIVEN a session with one or more questions
- WHEN `findBySession(sessionId)` is called
- THEN it MUST return all questions for that session

#### Scenario: Find by status
- GIVEN multiple persisted questions with various statuses
- WHEN `findByStatus(status)` is called
- THEN it MUST return only questions matching the given status

#### Scenario: Find pending for session
- GIVEN a session with a question in `WAITING_FOR_ANSWER`
- WHEN `findPending(sessionId)` is called
- THEN it MUST return that question
- AND it MUST NOT return questions in other statuses

#### Scenario: Delete question
- GIVEN a persisted question
- WHEN `delete(questionId)` is called
- THEN the question MUST be removed from the store

### Requirement: LealoneQuestionStore

The system MUST provide a `LealoneQuestionStore` implementing `QuestionStore` backed by a Lealone SQL table.

#### Scenario: Auto-create table on init
- GIVEN a fresh database
- WHEN `LealoneQuestionStore` is constructed
- THEN the questions table MUST be created automatically

#### Scenario: Expire timed-out questions
- GIVEN a question in `WAITING_FOR_ANSWER` with an `expiresAt` timestamp in the past
- WHEN the background scanner runs
- THEN the question status MUST transition to `EXPIRED`
- AND a `QUESTION_EXPIRED` event MUST be published

### Requirement: MobileChannelConfig

The system MUST define a `MobileChannelConfig` record in `org.specdriven.agent.question` for configuring a single mobile interaction channel.

#### Scenario: Config contains required fields
- GIVEN a `MobileChannelConfig` instance
- THEN it MUST expose `channelType` (String)
- AND it MUST expose `vaultKey` (String)
- AND it MUST expose `overrides` (Map<String, String>)

#### Scenario: Config rejects missing channel type
- GIVEN a `MobileChannelConfig` construction with null or blank `channelType`
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: Config rejects missing vault key
- GIVEN a `MobileChannelConfig` construction with null or blank `vaultKey`
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: Overrides default to empty map
- GIVEN a `MobileChannelConfig` construction without explicit overrides
- THEN `overrides` MUST return an empty map

### Requirement: MobileChannelHandle

The system MUST define a `MobileChannelHandle` record in `org.specdriven.agent.question` wrapping a matched delivery channel and reply collector pair.

#### Scenario: Handle exposes channel and collector
- GIVEN a `MobileChannelHandle` instance
- THEN it MUST expose a `QuestionDeliveryChannel channel()`
- AND it MUST expose a `QuestionReplyCollector collector()`

### Requirement: MobileChannelProvider

The system MUST define a `MobileChannelProvider` functional interface in `org.specdriven.agent.question` for creating channel handles from config.

#### Scenario: Provider creates handle from config
- GIVEN a registered `MobileChannelProvider` and a valid `MobileChannelConfig`
- WHEN `create(config)` is called
- THEN it MUST return a non-null `MobileChannelHandle`

### Requirement: MobileChannelRegistry

The system MUST define a `MobileChannelRegistry` in `org.specdriven.agent.question` for managing named channel providers and assembling channel handles from config.

#### Scenario: Register provider by name
- GIVEN a `MobileChannelRegistry` instance
- WHEN `registerProvider("telegram", provider)` is called
- THEN `provider("telegram")` MUST return that provider

#### Scenario: Reject duplicate provider name
- GIVEN a registry with a provider registered under "telegram"
- WHEN `registerProvider("telegram", otherProvider)` is called
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: List registered providers
- GIVEN a registry with two providers registered
- WHEN `registeredProviders()` is called
- THEN it MUST return a set containing both provider names

#### Scenario: Assemble all configured channels
- GIVEN a registry with providers registered for "telegram" and "discord"
- AND a list of two `MobileChannelConfig` instances with matching types
- WHEN `assembleAll(configs)` is called
- THEN it MUST return a list of `MobileChannelHandle` in config order
- AND each handle MUST be produced by the matching provider

#### Scenario: Reject unknown channel type on assembly
- GIVEN a registry with no provider for "slack"
- AND a config with `channelType` "slack"
- WHEN `assembleAll(configs)` is called
- THEN it MUST throw `IllegalArgumentException` identifying the unknown type

#### Scenario: Assemble empty config list
- GIVEN a registry with registered providers
- AND an empty config list
- WHEN `assembleAll(configs)` is called
- THEN it MUST return an empty list

### Requirement: MobileAdapterException

The system MUST define a `MobileAdapterException` in `org.specdriven.agent.question` for adapter-specific failures.

#### Scenario: Exception carries channel type
- GIVEN a `MobileAdapterException` constructed with channel type "telegram" and a message
- THEN `channelType()` MUST return "telegram"
- AND `getMessage()` MUST return the provided message

#### Scenario: Exception wraps cause
- GIVEN a `MobileAdapterException` constructed with a cause
- THEN `getCause()` MUST return the provided cause

### Requirement: RichMessageFormatter

The system MUST define a `RichMessageFormatter` interface in `org.specdriven.agent.question` for extensible message formatting.

#### Scenario: Format question as plain text
- GIVEN a `Question` instance
- WHEN `format(question)` is called on the default `PlainTextFormatter`
- THEN the result MUST be a non-empty string containing the question text, impact, recommendation, sessionId, and questionId

### Requirement: BuiltinMobileAdapters

The system MUST provide `BuiltinMobileAdapters` in `org.specdriven.agent.question` for auto-registering built-in channel providers.

#### Scenario: Register all built-in providers
- GIVEN an empty `MobileChannelRegistry`, a `QuestionRuntime`, and a `SecretVault`
- WHEN `BuiltinMobileAdapters.registerAll(registry, runtime, vault)` is called
- THEN the registry MUST contain providers for "telegram" and "discord"

#### Scenario: Built-in provider names
- GIVEN `BuiltinMobileAdapters` class
- THEN it MUST expose `TELEGRAM` and `DISCORD` constant strings with values "telegram" and "discord"

### Requirement: TelegramDeliveryChannel

The system MUST provide a `TelegramDeliveryChannel` implementing `QuestionDeliveryChannel` that sends question notifications via the Telegram Bot API.

#### Scenario: Send question to Telegram chat
- GIVEN a `TelegramDeliveryChannel` configured with a valid bot token and chat ID
- AND a `Question` with status `WAITING_FOR_ANSWER`
- WHEN `send(question)` is called
- THEN an HTTP POST MUST be sent to `https://api.telegram.org/bot{token}/sendMessage`
- AND the request body MUST include `chat_id` and `text` fields
- AND `text` MUST contain the question text, impact, and recommendation

#### Scenario: Telegram API error throws MobileAdapterException
- GIVEN a `TelegramDeliveryChannel` configured with an invalid bot token
- WHEN `send(question)` is called
- THEN it MUST throw `MobileAdapterException` with channel type "telegram"

#### Scenario: Resolve credentials from vault
- GIVEN a `TelegramDeliveryChannel` constructed with a `SecretVault`
- WHEN the channel sends a question
- THEN the bot token MUST be resolved from the vault using the configured vault key with `.token` suffix

### Requirement: TelegramReplyCollector

The system MUST provide a `TelegramReplyCollector` implementing `QuestionReplyCollector` that receives human replies from Telegram webhook callbacks.

#### Scenario: Collect valid Telegram reply
- GIVEN a `TelegramReplyCollector` with a shared message map containing a mapping from a Telegram message_id to a sessionId
- AND a pending question in `WAITING_FOR_ANSWER` for that session
- AND a valid Telegram Update JSON payload with a `message.reply_to_message.message_id` matching the mapped message
- WHEN `processCallback(jsonPayload)` is called
- THEN an `Answer` MUST be constructed with `source == HUMAN_MOBILE`
- AND `deliveryMode == PUSH_MOBILE_WAIT_HUMAN`
- AND the answer MUST be submitted to the `QuestionRuntime`

#### Scenario: Reject callback without message field
- GIVEN a `TelegramReplyCollector`
- AND a JSON payload with no `message` field
- WHEN `processCallback(jsonPayload)` is called
- THEN it MUST throw `MobileAdapterException` with channel type "telegram"

#### Scenario: Reject callback that is not a reply
- GIVEN a `TelegramReplyCollector`
- AND a JSON payload with a `message` but no `reply_to_message`
- WHEN `processCallback(jsonPayload)` is called
- THEN it MUST throw `MobileAdapterException` with channel type "telegram"

### Requirement: TelegramChannelProvider

The system MUST provide a `TelegramChannelProvider` implementing `MobileChannelProvider`.

#### Scenario: Create handle from config
- GIVEN a `MobileChannelConfig` with `channelType == "telegram"`
- AND valid vault entries for the bot token
- WHEN `create(config)` is called
- THEN it MUST return a `MobileChannelHandle` with a `TelegramDeliveryChannel` and `TelegramReplyCollector`

#### Scenario: Config requires chatId override
- GIVEN a `MobileChannelConfig` with `channelType == "telegram"` but no `chatId` in overrides
- WHEN `create(config)` is called
- THEN it MUST throw `MobileAdapterException` indicating missing chatId

### Requirement: DiscordDeliveryChannel

The system MUST provide a `DiscordDeliveryChannel` implementing `QuestionDeliveryChannel` that sends question notifications via Discord webhook.

#### Scenario: Send question to Discord channel
- GIVEN a `DiscordDeliveryChannel` configured with a valid webhook URL
- AND a `Question` with status `WAITING_FOR_ANSWER`
- WHEN `send(question)` is called
- THEN an HTTP POST MUST be sent to the webhook URL
- AND the request body MUST include a `content` field with the formatted question text

#### Scenario: Discord webhook error throws MobileAdapterException
- GIVEN a `DiscordDeliveryChannel` configured with an invalid webhook URL
- WHEN `send(question)` is called
- THEN it MUST throw `MobileAdapterException` with channel type "discord"

#### Scenario: Resolve webhook URL from vault
- GIVEN a `DiscordDeliveryChannel` constructed with a `SecretVault`
- WHEN the channel sends a question
- THEN the webhook URL MUST be resolved from the vault using the configured vault key with `.webhookUrl` suffix

### Requirement: DiscordReplyCollector

The system MUST provide a `DiscordReplyCollector` implementing `QuestionReplyCollector` that receives human replies from Discord interaction callbacks.

#### Scenario: Collect valid Discord reply
- GIVEN a `DiscordReplyCollector` with a shared message map and a known webhook secret
- AND a pending question in `WAITING_FOR_ANSWER` for a session mapped by message_id
- AND a valid Discord interaction JSON payload with `message_reference.message_id` matching the mapped message
- AND a valid HMAC-SHA256 signature in the header
- WHEN `processCallback(jsonPayload, signatureHeader)` is called
- THEN an `Answer` MUST be constructed with `source == HUMAN_MOBILE`
- AND `deliveryMode == PUSH_MOBILE_WAIT_HUMAN`
- AND the answer MUST be submitted to the `QuestionRuntime`

#### Scenario: Reject callback with invalid signature
- GIVEN a `DiscordReplyCollector` expecting a specific webhook secret
- AND a callback with an invalid HMAC-SHA256 signature
- WHEN `processCallback(jsonPayload, signatureHeader)` is called
- THEN it MUST throw `MobileAdapterException` with channel type "discord"

#### Scenario: Reject callback without message_reference
- GIVEN a `DiscordReplyCollector`
- AND a validly signed JSON payload with no `message_reference`
- WHEN `processCallback(jsonPayload, signatureHeader)` is called
- THEN it MUST throw `MobileAdapterException` with channel type "discord"

### Requirement: DiscordChannelProvider

The system MUST provide a `DiscordChannelProvider` implementing `MobileChannelProvider`.

#### Scenario: Create handle from config
- GIVEN a `MobileChannelConfig` with `channelType == "discord"`
- AND valid vault entries for the webhook URL
- WHEN `create(config)` is called
- THEN it MUST return a `MobileChannelHandle` with a `DiscordDeliveryChannel` and `DiscordReplyCollector`

#### Scenario: Config requires callbackBaseUrl override
- GIVEN a `MobileChannelConfig` with `channelType == "discord"` but no `callbackBaseUrl` in overrides
- WHEN `create(config)` is called
- THEN it MUST throw `MobileAdapterException` indicating missing callbackBaseUrl

### Requirement: QuestionMessageTemplate

The system MUST define a `QuestionMessageTemplate` in `org.specdriven.agent.question` that implements `RichMessageFormatter` and produces channel-specific formatted messages from a `Question` with field policy and masking applied.

#### Scenario: Template formats question with all included fields
- GIVEN a `QuestionMessageTemplate` with default `INCLUDE` policy for all fields
- AND a `Question` instance
- WHEN `format(question)` is called
- THEN the result MUST contain the question text, impact, recommendation, sessionId, and questionId

#### Scenario: Template applies TRIM policy
- GIVEN a `QuestionMessageTemplate` with `TRIM` policy for the `sessionId` field
- AND a `Question` instance
- WHEN `format(question)` is called
- THEN the result MUST NOT contain the sessionId value

#### Scenario: Template applies MASK policy
- GIVEN a `QuestionMessageTemplate` with `MASK` policy for the `sessionId` field
- AND a `MaskingStrategy` that masks session IDs
- AND a `Question` instance
- WHEN `format(question)` is called
- THEN the result MUST contain a masked version of the sessionId
- AND the raw sessionId MUST NOT appear in the result

#### Scenario: Template substitutes default copy for empty fields
- GIVEN a `QuestionMessageTemplate` with a default text of "N/A"
- AND a `Question` instance with an empty `recommendation` field
- WHEN `format(question)` is called
- THEN the recommendation section MUST display "N/A" instead of an empty string

#### Scenario: Template exposes target channel type
- GIVEN a `QuestionMessageTemplate` instance
- THEN it MUST expose a `channelType` (String) identifying the target channel

### Requirement: TemplateFieldPolicy

The system MUST define a `TemplateFieldPolicy` enum in `org.specdriven.agent.question` controlling how individual question fields are rendered in a template.

#### Scenario: Required policy values
- THEN `TemplateFieldPolicy` MUST include `INCLUDE`
- AND it MUST include `TRIM`
- AND it MUST include `MASK`

#### Scenario: INCLUDE renders the field value as-is
- GIVEN a field with `INCLUDE` policy
- WHEN the template is rendered
- THEN the field value MUST appear in the output unchanged

#### Scenario: TRIM omits the field entirely
- GIVEN a field with `TRIM` policy
- WHEN the template is rendered
- THEN neither the field label nor the field value MUST appear in the output

#### Scenario: MASK applies masking strategy
- GIVEN a field with `MASK` policy and a `MaskingStrategy`
- WHEN the template is rendered
- THEN the field value MUST be transformed by the masking strategy before inclusion

### Requirement: MaskingStrategy

The system MUST define a `MaskingStrategy` functional interface in `org.specdriven.agent.question` for transforming sensitive field values.

#### Scenario: Mask a field value
- GIVEN a `MaskingStrategy` and a field name and value
- WHEN `mask(fieldName, value)` is called
- THEN it MUST return a non-null masked string

#### Scenario: Mask null value returns placeholder
- GIVEN a `MaskingStrategy` and a null value
- WHEN `mask(fieldName, null)` is called
- THEN it MUST return a fixed placeholder string

### Requirement: DefaultMaskingStrategy

The system MUST provide a `DefaultMaskingStrategy` in `org.specdriven.agent.question` implementing common masking patterns.

#### Scenario: Mask email address
- GIVEN a `DefaultMaskingStrategy`
- WHEN an email address "user@example.com" is masked
- THEN the result MUST reveal at most the first two characters before the @ sign
- AND the domain part MUST be fully masked

#### Scenario: Mask API key or token
- GIVEN a `DefaultMaskingStrategy`
- WHEN a string longer than 8 characters is masked as an API key
- THEN the result MUST reveal at most the first 4 characters
- AND the rest MUST be replaced with a fixed mask character

#### Scenario: Mask generic value
- GIVEN a `DefaultMaskingStrategy`
- WHEN a short string (4 characters or fewer) is masked
- THEN the result MUST be a fixed placeholder

### Requirement: TelegramMessageTemplate

The system MUST provide a `TelegramMessageTemplate` extending `QuestionMessageTemplate` that formats questions for Telegram using MarkdownV2-compatible syntax.

#### Scenario: Telegram template includes MarkdownV2 formatting
- GIVEN a `TelegramMessageTemplate` and a `Question` instance
- WHEN `format(question)` is called
- THEN the result MUST use Telegram MarkdownV2 bold markers for field labels

#### Scenario: Telegram template channel type
- GIVEN a `TelegramMessageTemplate` instance
- THEN `channelType` MUST return "telegram"

### Requirement: DiscordMessageTemplate

The system MUST provide a `DiscordMessageTemplate` extending `QuestionMessageTemplate` that formats questions for Discord using markdown-compatible syntax.

#### Scenario: Discord template includes markdown formatting
- GIVEN a `DiscordMessageTemplate` and a `Question` instance
- WHEN `format(question)` is called
- THEN the result MUST use markdown bold markers for field labels

#### Scenario: Discord template channel type
- GIVEN a `DiscordMessageTemplate` instance
- THEN `channelType` MUST return "discord"

### Requirement: Template-aware delivery channels

The existing delivery channels MUST accept `QuestionMessageTemplate` instances as `RichMessageFormatter` without behavioral change to their public API.

#### Scenario: TelegramDeliveryChannel uses template formatter
- GIVEN a `TelegramDeliveryChannel` constructed with a `TelegramMessageTemplate`
- AND a `Question` with status `WAITING_FOR_ANSWER`
- WHEN `send(question)` is called
- THEN the message text MUST be formatted by the template
- AND the Telegram API request MUST include the templated text

#### Scenario: DiscordDeliveryChannel uses template formatter
- GIVEN a `DiscordDeliveryChannel` constructed with a `DiscordMessageTemplate`
- AND a `Question` with status `WAITING_FOR_ANSWER`
- WHEN `send(question)` is called
- THEN the message content MUST be formatted by the template
- AND the Discord webhook request MUST include the templated content

### Requirement: ReplyCallbackRouter

The system MUST provide a `ReplyCallbackRouter` in `org.specdriven.agent.question` that maps channel type names to assembled `QuestionReplyCollector` instances and dispatches incoming callback payloads.

#### Scenario: Register collector by channel type
- GIVEN a `ReplyCallbackRouter` instance
- WHEN `register("telegram", collector)` is called
- THEN subsequent dispatches for "telegram" MUST route to that collector

#### Scenario: Reject duplicate channel type registration
- GIVEN a router with a collector registered for "telegram"
- WHEN `register("telegram", otherCollector)` is called
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: Dispatch valid Telegram callback
- GIVEN a router with a registered Telegram collector and webhook secret
- AND an incoming payload with a valid `X-Telegram-Bot-Api-Secret-Token` header
- WHEN `dispatch("telegram", payload, headers)` is called
- THEN the payload MUST be forwarded to the registered Telegram collector's `processCallback(payload)`

#### Scenario: Reject Telegram callback with invalid secret
- GIVEN a router with a registered Telegram collector and expected webhook secret
- AND an incoming payload with a mismatched or missing `X-Telegram-Bot-Api-Secret-Token` header
- WHEN `dispatch("telegram", payload, headers)` is called
- THEN it MUST throw `MobileAdapterException` with channel type "telegram"

#### Scenario: Dispatch valid Discord callback
- GIVEN a router with a registered Discord collector
- AND an incoming payload with an `X-Signature-256` header
- WHEN `dispatch("discord", payload, headers)` is called
- THEN the payload and signature MUST be forwarded to the registered Discord collector's `processCallback(payload, signature)`

#### Scenario: Reject unknown channel type
- GIVEN a router with no collector registered for "slack"
- WHEN `dispatch("slack", payload, headers)` is called
- THEN it MUST throw `IllegalArgumentException` identifying the unknown channel type

#### Scenario: List registered channel types
- GIVEN a router with collectors registered for "telegram" and "discord"
- WHEN `registeredChannels()` is called
- THEN it MUST return a set containing "telegram" and "discord"

### Requirement: Callback HTTP endpoint

The system MUST expose HTTP callback endpoints that receive webhook payloads from external mobile channels and dispatch them through the `ReplyCallbackRouter`.

#### Scenario: Telegram callback endpoint
- GIVEN a running HTTP server with a registered Telegram collector in the router
- WHEN a `POST /api/v1/callbacks/telegram` request arrives with a valid JSON body and correct `X-Telegram-Bot-Api-Secret-Token` header
- THEN the server MUST respond with HTTP 200
- AND the body MUST be dispatched through the router to the Telegram collector

#### Scenario: Discord callback endpoint
- GIVEN a running HTTP server with a registered Discord collector in the router
- WHEN a `POST /api/v1/callbacks/discord` request arrives with a valid JSON body and `X-Signature-256` header
- THEN the server MUST respond with HTTP 200
- AND the body and signature MUST be dispatched through the router to the Discord collector

#### Scenario: Callback endpoint bypasses auth
- GIVEN a running HTTP server with auth middleware
- WHEN a `POST /api/v1/callbacks/{channelType}` request arrives without an auth token
- THEN the request MUST still be processed (not rejected by auth middleware)

#### Scenario: Unknown channel type returns 404
- GIVEN a running HTTP server
- WHEN a `POST /api/v1/callbacks/unknown` request arrives
- THEN the server MUST respond with HTTP 404

#### Scenario: Invalid Telegram secret returns 401
- GIVEN a running HTTP server with a registered Telegram collector
- WHEN a `POST /api/v1/callbacks/telegram` request arrives with an invalid secret token header
- THEN the server MUST respond with HTTP 401

#### Scenario: Callback error returns structured error
- GIVEN a running HTTP server
- WHEN a callback dispatch causes an exception
- THEN the server MUST respond with an appropriate HTTP error code and a structured JSON error body

### Requirement: DeliveryStatus enum

The system MUST define a `DeliveryStatus` enum in `org.specdriven.agent.question` for tracking the outcome of each channel send attempt.

#### Scenario: Required delivery statuses
- THEN `DeliveryStatus` MUST include `PENDING`
- AND `DeliveryStatus` MUST include `SENT`
- AND `DeliveryStatus` MUST include `FAILED`
- AND `DeliveryStatus` MUST include `RETRYING`

### Requirement: DeliveryAttempt record

The system MUST define a `DeliveryAttempt` record in `org.specdriven.agent.question` capturing a single delivery attempt.

#### Scenario: Attempt contains required fields
- GIVEN a `DeliveryAttempt` instance
- THEN it MUST expose `questionId` (String)
- AND it MUST expose `channelType` (String)
- AND it MUST expose `attemptNumber` (int, >= 1)
- AND it MUST expose `status` (DeliveryStatus)
- AND it MUST expose `statusCode` (Integer, nullable)
- AND it MUST expose `errorMessage` (String, nullable)
- AND it MUST expose `attemptedAt` (long, epoch millis)

### Requirement: RetryConfig record

The system MUST define a `RetryConfig` record in `org.specdriven.agent.question` for configuring delivery retry behavior.

#### Scenario: Config has defaults
- GIVEN a `RetryConfig` constructed with no arguments
- THEN `maxAttempts` MUST be 3
- AND `initialDelayMs` MUST be 1000
- AND `backoffMultiplier` MUST be 2.0

#### Scenario: Config validates maxAttempts
- GIVEN a `RetryConfig` with `maxAttempts` less than 1
- WHEN constructed
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: Config validates initialDelayMs
- GIVEN a `RetryConfig` with `initialDelayMs` less than or equal to 0
- WHEN constructed
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: Config validates backoffMultiplier
- GIVEN a `RetryConfig` with `backoffMultiplier` less than 1.0
- WHEN constructed
- THEN it MUST throw `IllegalArgumentException`

### Requirement: DeliveryLogStore interface

The system MUST define a `DeliveryLogStore` interface in `org.specdriven.agent.question` for persisting delivery attempts.

#### Scenario: Save delivery attempt
- GIVEN a `DeliveryAttempt` instance
- WHEN `save(attempt)` is called
- THEN the attempt MUST be persisted

#### Scenario: Find attempts by question
- GIVEN multiple delivery attempts for a questionId
- WHEN `findByQuestion(questionId)` is called
- THEN it MUST return all attempts for that questionId ordered by attemptNumber

#### Scenario: Find latest attempt
- GIVEN multiple delivery attempts for a questionId
- WHEN `findLatestByQuestion(questionId)` is called
- THEN it MUST return the attempt with the highest attemptNumber

#### Scenario: Return empty for unknown question
- GIVEN no delivery attempts for a questionId
- WHEN `findByQuestion(questionId)` is called
- THEN it MUST return an empty list

### Requirement: LealoneDeliveryLogStore

The system MUST provide a `LealoneDeliveryLogStore` implementing `DeliveryLogStore` backed by a Lealone SQL table.

#### Scenario: Auto-create table on init
- GIVEN a fresh database
- WHEN `LealoneDeliveryLogStore` is constructed
- THEN a `delivery_log` table MUST be created automatically

### Requirement: Delivery log storage mapping compatibility

The system MUST define a delivery-log storage mapping that preserves the current
`DeliveryLogStore` behavior against the existing `delivery_log` table.

#### Scenario: Delivery attempt fields round-trip through mapped storage

- GIVEN a `LealoneDeliveryLogStore` backed by a fresh database
- WHEN a `DeliveryAttempt` is saved and then read by question ID
- THEN the returned attempt MUST preserve `questionId`, `channelType`,
  `attemptNumber`, `status`, `statusCode`, `errorMessage`, and `attemptedAt`

#### Scenario: Nullable delivery attempt fields remain supported

- GIVEN a `DeliveryAttempt` with a null `statusCode` or null `errorMessage`
- WHEN the attempt is saved and read back
- THEN the corresponding returned field MUST remain null

#### Scenario: Existing delivery log table rows remain readable

- GIVEN a row compatible with the existing `delivery_log` table columns
- WHEN `findByQuestion(questionId)` is called for that row's question
- THEN the row MUST be returned as a `DeliveryAttempt`

#### Scenario: Delivery log lookup ordering remains stable

- GIVEN multiple delivery attempts for the same question
- WHEN `findByQuestion(questionId)` is called
- THEN attempts MUST be returned in ascending `attemptNumber` order

#### Scenario: Latest delivery attempt lookup remains stable

- GIVEN multiple delivery attempts for the same question
- WHEN `findLatestByQuestion(questionId)` is called
- THEN the returned attempt MUST be the one with the highest `attemptNumber`

#### Scenario: Delivery log public contract remains unchanged

- GIVEN callers use the existing `DeliveryLogStore` methods
- WHEN delivery-log storage mapping is introduced
- THEN callers MUST NOT need new method signatures, new input fields, or a new
  store construction contract

### Requirement: Delivery log table interoperability

The system MUST preserve bidirectional interoperability between
`LealoneDeliveryLogStore` operations and the existing `delivery_log` table
columns.

#### Scenario: Store-saved attempts remain table-visible

- GIVEN a `LealoneDeliveryLogStore` backed by a fresh Lealone database
- WHEN a caller saves a `DeliveryAttempt` through the Store
- THEN a row MUST be visible in the existing `delivery_log` table columns with
  the saved attempt values

#### Scenario: Compatible table rows remain store-readable

- GIVEN a row inserted directly into the existing `delivery_log` table columns
- WHEN `findByQuestion(questionId)` is called for that row's question
- THEN the Store MUST return a matching `DeliveryAttempt`

#### Scenario: Lookup behavior remains stable through the pilot

- GIVEN multiple delivery attempts for the same question
- WHEN callers use `findByQuestion(questionId)` and
  `findLatestByQuestion(questionId)`
- THEN attempts MUST remain ordered by ascending `attemptNumber` for
  `findByQuestion`
- AND latest lookup MUST return the attempt with the highest `attemptNumber`

#### Scenario: Public delivery log API remains unchanged

- GIVEN existing callers construct and use `LealoneDeliveryLogStore`
- WHEN the delivery-log pilot is completed
- THEN callers MUST NOT need new method signatures, new input fields, or a new
  Store construction contract

### Requirement: Delivery lifecycle events

The system MUST emit events for delivery attempt outcomes.

#### Scenario: Required delivery event types
- THEN the event model MUST include `DELIVERY_ATTEMPTED`
- AND it MUST include `DELIVERY_SUCCEEDED`
- AND it MUST include `DELIVERY_FAILED`

#### Scenario: Delivery-attempted event metadata
- GIVEN a `DELIVERY_ATTEMPTED` event
- THEN its metadata MUST include `questionId`, `channelType`, and `attemptNumber`

#### Scenario: Delivery-succeeded event metadata
- GIVEN a `DELIVERY_SUCCEEDED` event
- THEN its metadata MUST include `questionId`, `channelType`, and `attemptNumber`

#### Scenario: Delivery-failed event metadata
- GIVEN a `DELIVERY_FAILED` event
- THEN its metadata MUST include `questionId`, `channelType`, `attemptNumber`, and `errorMessage`

### Requirement: RetryingDeliveryChannel

The system MUST provide a `RetryingDeliveryChannel` implementing `QuestionDeliveryChannel` that decorates another channel with retry logic.

#### Scenario: Successful first attempt
- GIVEN a `RetryingDeliveryChannel` wrapping a channel that succeeds
- WHEN `send(question)` is called
- THEN the underlying channel MUST be called once
- AND a `DeliveryAttempt` with status `SENT` MUST be logged
- AND a `DELIVERY_SUCCEEDED` event MUST be emitted

#### Scenario: Retry on transient failure
- GIVEN a `RetryingDeliveryChannel` with `maxAttempts == 3`
- AND a channel that fails on the first two attempts and succeeds on the third
- WHEN `send(question)` is called
- THEN the underlying channel MUST be called 3 times
- AND `DeliveryAttempt` records MUST be logged for each attempt
- AND the first two attempts MUST have status `RETRYING`
- AND the final attempt MUST have status `SENT`
- AND a `DELIVERY_SUCCEEDED` event MUST be emitted

#### Scenario: Exhausted retries
- GIVEN a `RetryingDeliveryChannel` with `maxAttempts == 2`
- AND a channel that always throws `MobileAdapterException`
- WHEN `send(question)` is called
- THEN the underlying channel MUST be called 2 times
- AND a `DELIVERY_FAILED` event MUST be emitted
- AND a `MobileAdapterException` MUST be thrown

#### Scenario: Non-adapter exception propagates immediately
- GIVEN a `RetryingDeliveryChannel` wrapping a channel that throws `NullPointerException`
- WHEN `send(question)` is called
- THEN the exception MUST propagate without retry
- AND no `DELIVERY_FAILED` event MUST be emitted

#### Scenario: Exponential backoff between retries
- GIVEN a `RetryingDeliveryChannel` with `initialDelayMs == 100` and `backoffMultiplier == 2.0`
- AND a channel that fails twice then succeeds
- WHEN `send(question)` is called
- THEN the delay before the second attempt MUST be approximately 100ms
- AND the delay before the third attempt MUST be approximately 200ms

### Requirement: RetryingDeliveryChannel integration

The system MUST wrap assembled delivery channels with retry in `BuiltinMobileAdapters`.

#### Scenario: Built-in adapters use retry wrapper
- GIVEN an empty `MobileChannelRegistry`, a `QuestionRuntime`, a `SecretVault`, a `DeliveryLogStore`, and a `RetryConfig`
- WHEN `BuiltinMobileAdapters.registerAll(registry, runtime, vault, logStore, retryConfig)` is called
- THEN each assembled `MobileChannelHandle` MUST contain a `RetryingDeliveryChannel` wrapping the original channel

### Requirement: Delivery status HTTP endpoint

The system MUST expose an HTTP endpoint for querying delivery status.

#### Scenario: Query delivery status by question
- GIVEN a running HTTP server with delivery log entries for questionId "q1"
- WHEN `GET /api/v1/delivery/status/q1` is requested with valid auth
- THEN the server MUST respond with HTTP 200
- AND the response body MUST be a JSON array of delivery attempts for that question

#### Scenario: Empty result for unknown question
- GIVEN a running HTTP server with no delivery entries for questionId "q99"
- WHEN `GET /api/v1/delivery/status/q99` is requested with valid auth
- THEN the server MUST respond with HTTP 200
- AND the response body MUST be an empty JSON array

#### Scenario: Delivery status endpoint requires auth
- GIVEN a running HTTP server with auth middleware
- WHEN `GET /api/v1/delivery/status/q1` is requested without auth
- THEN the server MUST respond with HTTP 401

## ADDED Requirements — interactive-session-interface

### Requirement: Interactive session remains subordinate to Question/Answer lifecycle

- The first interactive session contract MUST be defined as a future integration boundary for human handling of waiting questions
- This contract change MUST NOT bypass the existing `Question` and `Answer` lifecycle
- This contract change MUST NOT change waiting-question persistence, routing, expiration, or answer submission semantics

#### Scenario: Interactive contract introduction does not replace waiting question semantics

- GIVEN a session that already uses the existing waiting-question lifecycle
- WHEN the `InteractiveSession` contract is introduced
- THEN waiting questions MUST continue to be represented by `Question` records and statuses
- AND answer submission MUST remain governed by existing `QuestionRuntime` behavior

#### Scenario: Later interactive reply handling can target an existing waiting question

- GIVEN a later roadmap change that adds an interactive bridge for human replies
- WHEN that bridge is implemented
- THEN it MUST be able to use `InteractiveSession` as the input/output surface while still resolving replies through the existing `Question` and `Answer` lifecycle
- AND this change MUST NOT itself define new answer-routing behavior

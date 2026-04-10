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

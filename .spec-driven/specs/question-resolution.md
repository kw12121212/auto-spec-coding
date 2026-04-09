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

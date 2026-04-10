# Question Delivery Surface Delta Spec

## ADDED Requirements

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

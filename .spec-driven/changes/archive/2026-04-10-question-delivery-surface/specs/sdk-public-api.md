# SDK Question API Delta Spec

## ADDED Requirements

### Requirement: SdkBuilder delivery mode override

The `SdkBuilder` MUST support a global delivery mode override that applies to all agents.

#### Scenario: Override delivery mode
- GIVEN a builder with `.deliveryModeOverride(DeliveryMode.PAUSE_WAIT_HUMAN)`
- WHEN an agent is created and encounters a question
- THEN the question MUST be routed using `PAUSE_WAIT_HUMAN` regardless of the default routing policy

#### Scenario: No override uses routing policy
- GIVEN a builder without `deliveryModeOverride`
- WHEN an agent is created and encounters a question
- THEN the question MUST be routed using the default `QuestionRoutingPolicy`

### Requirement: SdkAgent pending question query

The `SdkAgent` MUST expose pending questions for a session.

#### Scenario: Query returns waiting questions
- GIVEN a session with one question in `WAITING_FOR_ANSWER`
- WHEN `pendingQuestions(sessionId)` is called
- THEN it MUST return a list containing that question

#### Scenario: Empty list when no pending questions
- GIVEN a session with no waiting questions
- WHEN `pendingQuestions(sessionId)` is called
- THEN it MUST return an empty list

### Requirement: SdkAgent human reply submission

The `SdkAgent` MUST support submitting a human reply to a waiting question.

#### Scenario: Submit valid reply
- GIVEN a session with a question in `WAITING_FOR_ANSWER`
- AND a valid `Answer` with matching `questionId`, `sessionId`, and `deliveryMode`
- WHEN `submitHumanReply(sessionId, questionId, answer)` is called
- THEN the answer MUST be accepted
- AND the question status MUST transition to `ANSWERED`
- AND a `QUESTION_ANSWERED` event MUST be emitted

#### Scenario: Reject reply for unknown session
- GIVEN no waiting question for the given session
- WHEN `submitHumanReply(sessionId, questionId, answer)` is called
- THEN it MUST throw `SdkException` with `isRetryable()` returning `false`

#### Scenario: Reject reply for expired question
- GIVEN a question whose status is `EXPIRED`
- WHEN `submitHumanReply(sessionId, questionId, answer)` is called
- THEN it MUST throw `SdkException` with `isRetryable()` returning `false`

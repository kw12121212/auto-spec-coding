# Question Resolution Spec (delta)

## ADDED Requirements

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

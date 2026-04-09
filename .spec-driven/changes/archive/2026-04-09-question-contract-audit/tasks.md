# Tasks: question-contract-audit

## Implementation

- [x] Add a new delta spec `specs/question-resolution.md` defining `Question`, `Answer`, `AnswerSource`, `QuestionStatus`, `QuestionDecision`, and `DeliveryMode`
- [x] Define the minimum structured question payload contract with required `question`, `impact`, and `recommendation` fields
- [x] Define required answer attribution fields (`source`, `basisSummary`, `confidence`) and conditional escalation metadata requirements
- [x] Define lifecycle and audit requirements for question creation, answering, escalation, expiry, and source traceability
- [x] Add a delta spec for `event-system.md` extending the event model with question lifecycle event types

## Testing

- [x] Run `mvn -q -DskipTests compile` to validate the repository still compiles after implementing the change
- [x] Run `mvn -q test` to execute the repository unit test suite
- [x] Add unit tests covering required question payload validation, answer field requirements, delivery-mode serialization, and audit event metadata
- [x] Add unit tests covering conditional `escalationReason` requirements for escalated and human-routed answers

## Verification

- [x] Verify SDK public APIs are unchanged in this change
- [x] Verify pause/resume runtime behavior is not introduced by this change
- [x] Verify the new question contract is sufficient for later `orchestrator-question-pause` and `question-delivery-surface` work without redefining payload fields

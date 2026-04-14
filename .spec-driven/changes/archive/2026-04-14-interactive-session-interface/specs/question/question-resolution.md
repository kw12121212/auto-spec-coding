---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/interactive/InteractiveSession.java
    - src/main/java/org/specdriven/agent/question/Question.java
    - src/main/java/org/specdriven/agent/question/QuestionRuntime.java
  tests:
    - src/test/java/org/specdriven/agent/interactive/InteractiveSessionTest.java
    - src/test/java/org/specdriven/agent/question/QuestionRuntimeTest.java
---

# Question Resolution — Delta Spec: interactive-session-interface

## ADDED Requirements

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

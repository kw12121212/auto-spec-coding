---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/question/LealoneQuestionStore.java
    - src/main/java/org/specdriven/agent/question/QuestionModel.java
  tests:
    - src/test/java/org/specdriven/agent/question/LealoneQuestionStoreTest.java
---

# Question Resolution - Delta Spec: question-store-feasibility

## ADDED Requirements

### Requirement: Question store ORM feasibility boundary

The system MUST treat `LealoneQuestionStore` ORM adoption as a bounded
candidate-specific migration that preserves the existing `QuestionStore`
contract and `questions` table behavior.

#### Scenario: Question store public contract remains unchanged

- GIVEN existing callers construct and use `LealoneQuestionStore`
- WHEN the question-store feasibility migration is completed
- THEN callers MUST NOT need new method signatures, new input fields, or a new
  Store construction contract

#### Scenario: Other Stores remain outside the feasibility migration

- GIVEN the question-store feasibility migration is completed
- THEN Stores other than `LealoneQuestionStore` MUST remain on their existing
  persistence paths unless separately changed by another spec-driven change

### Requirement: Question table interoperability

The system MUST preserve bidirectional interoperability between
`LealoneQuestionStore` operations and the existing `questions` table columns.

#### Scenario: Store-saved questions remain table-visible

- GIVEN a `LealoneQuestionStore` backed by a fresh Lealone database
- WHEN a caller saves a `Question` through the Store
- THEN a row MUST be visible in the existing `questions` table columns with the
  saved question values

#### Scenario: Compatible table rows remain store-readable

- GIVEN a row inserted directly into the existing `questions` table columns
- WHEN the row is queried through `LealoneQuestionStore`
- THEN the Store MUST return a matching `Question`

#### Scenario: Question fields round-trip through mapped storage

- GIVEN a `Question` with a question ID, session ID, question text, impact,
  recommendation, status, category, and delivery mode
- WHEN the question is saved and read back through `LealoneQuestionStore`
- THEN all those fields MUST be preserved

#### Scenario: Saving an existing question remains update-compatible

- GIVEN a question already persisted through `LealoneQuestionStore`
- WHEN a caller saves another `Question` with the same question ID and changed
  field values
- THEN subsequent Store reads MUST return the changed field values for that
  question ID

#### Scenario: Question lookup behavior remains stable

- GIVEN multiple persisted questions across sessions and statuses
- WHEN callers use `findBySession(sessionId)`, `findByStatus(status)`, and
  `findPending(sessionId)`
- THEN each lookup MUST return only questions matching the requested session,
  status, or waiting-pending criteria

#### Scenario: Question update and delete behavior remains stable

- GIVEN a question persisted through `LealoneQuestionStore`
- WHEN callers update the question status or delete the question
- THEN the updated status MUST be observable through subsequent Store reads
- AND deleted questions MUST no longer be returned by subsequent Store reads

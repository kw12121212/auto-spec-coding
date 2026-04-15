---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/event/EventType.java
    - src/main/java/org/specdriven/agent/event/LealoneAuditLogStore.java
    - src/main/java/org/specdriven/agent/interactive/InteractiveCommandHandler.java
  tests:
    - src/test/java/org/specdriven/agent/event/EventSystemTest.java
    - src/test/java/org/specdriven/agent/event/LealoneAuditLogStoreTest.java
    - src/test/java/org/specdriven/agent/interactive/InteractiveCommandHandlerTest.java
---

## ADDED Requirements

### Requirement: Interactive command audit event

- The event model MUST include an interactive-command audit event type for human-in-the-loop command handling
- Interactive-command audit metadata MUST use only values supported by the existing event JSON serialization rules
- Interactive-command audit metadata MUST include `sessionId`
- Interactive-command audit metadata MUST include the handled command category or type
- Interactive-command audit metadata MUST include the raw command input or a stable rendered equivalent
- Interactive-command audit metadata MUST include an outcome field describing whether the command showed information, submitted an answer, closed the session, or was rejected as unknown
- Interactive-command audit metadata MAY include question identifiers or other scope fields when available from the waiting-question context

#### Scenario: SHOW command is auditable

- GIVEN an interactive command handler processes a `SHOW` command
- WHEN the command is accepted
- THEN an interactive-command audit event MUST be published
- AND the audit event metadata MUST identify the session and command outcome

#### Scenario: Answer command is auditable

- GIVEN an interactive command handler processes an answer command for a waiting question
- WHEN the answer is submitted via `QuestionRuntime`
- THEN an interactive-command audit event MUST be published
- AND the audit event metadata MUST identify the session and answer-submission outcome

#### Scenario: Unknown command is auditable

- GIVEN an interactive command handler processes an unknown command
- WHEN the guidance message is enqueued to the session output
- THEN an interactive-command audit event MUST be published
- AND the audit event metadata MUST identify the command as unrecognized

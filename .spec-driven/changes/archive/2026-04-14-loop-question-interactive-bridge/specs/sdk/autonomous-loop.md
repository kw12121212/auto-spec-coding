---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/loop/DefaultLoopDriver.java
    - src/main/java/org/specdriven/agent/loop/InteractiveSessionFactory.java
  tests:
    - src/test/java/org/specdriven/agent/loop/DefaultLoopDriverTest.java
---

## MODIFIED Requirements

### Requirement: DefaultLoopDriver
Previously: Constructor MUST accept `LoopConfig`, `LoopScheduler`, `LoopPipeline`, `LoopIterationStore`, `LoopAnswerAgent`, and `QuestionDeliveryService` (where `QuestionDeliveryService` MAY be null).

The DefaultLoopDriver MUST accept an additional optional constructor parameter `InteractiveSessionFactory` (nullable).
- When `InteractiveSessionFactory` is null, human-escalated pause behavior MUST remain identical to current behavior — no interactive session is created.
- When `InteractiveSessionFactory` is non-null and the loop transitions to `PAUSED` due to a human-escalated question:
  1. MUST call `factory.create(sessionId)` where `sessionId` is the current loop session identifier
  2. MUST call `start()` on the created `InteractiveSession`
  3. MUST publish `LOOP_INTERACTIVE_ENTERED` event with metadata: `sessionId` (String), `questionId` (String), `changeName` (String)
  4. MUST block the scheduling thread while the interactive session state is `ACTIVE`
  5. MUST periodically check whether the session has transitioned to `CLOSED` or `ERROR`
  6. When the session transitions to `CLOSED` or `ERROR`, MUST publish `LOOP_INTERACTIVE_EXITED` event with metadata: `sessionId` (String), `questionId` (String), `sessionEndState` (String — "CLOSED" or "ERROR")
  7. MUST NOT add the paused change name to `completedChangeNames` during the interactive session
  8. After session exit, the loop MUST remain in `PAUSED` state; resume MUST be triggered externally (via `resume()` call or stop via `stop()` call)
- The scheduling thread MUST react to `stop()` being called while an interactive session is active: MUST close the interactive session and exit the scheduling loop.

#### Scenario: No factory configured — existing pause behavior unchanged
- GIVEN a `DefaultLoopDriver` constructed without an `InteractiveSessionFactory`
- WHEN a human-escalated question causes the loop to pause
- THEN the loop MUST transition to `PAUSED` state
- AND no `InteractiveSession` MUST be created
- AND behavior MUST be identical to current pre-change behavior

#### Scenario: Factory configured — interactive session created on human escalation
- GIVEN a `DefaultLoopDriver` constructed with a non-null `InteractiveSessionFactory`
- AND a human-escalated question causes the loop to pause
- WHEN the escalation is processed
- THEN a new `InteractiveSession` MUST be created via `factory.create(sessionId)`
- AND `start()` MUST be called on the session
- AND `LOOP_INTERACTIVE_ENTERED` event MUST be published
- AND the scheduling thread MUST block while the session state is `ACTIVE`

#### Scenario: Interactive session closed — loop remains paused
- GIVEN a loop in `PAUSED` state with an active interactive session
- WHEN the session transitions to `CLOSED`
- THEN `LOOP_INTERACTIVE_EXITED` event MUST be published with `sessionEndState="CLOSED"`
- AND the loop state MUST remain `PAUSED`
- AND the scheduling thread MUST unblock

#### Scenario: Interactive session error — loop remains paused
- GIVEN a loop in `PAUSED` state with an active interactive session
- WHEN the session transitions to `ERROR`
- THEN `LOOP_INTERACTIVE_EXITED` event MUST be published with `sessionEndState="ERROR"`
- AND the loop state MUST remain `PAUSED`
- AND the scheduling thread MUST unblock

#### Scenario: Stop called during interactive session
- GIVEN a loop in `PAUSED` state with an active interactive session
- WHEN `stop()` is called
- THEN the interactive session MUST be closed
- AND `LOOP_INTERACTIVE_EXITED` event MUST be published
- AND the loop MUST transition to `STOPPED`

#### Scenario: Each pause creates a fresh session
- GIVEN a loop that pauses at a human-escalated question, enters interactive mode, session closes, and then `resume()` is called
- WHEN the loop later pauses at another human-escalated question
- THEN a new `InteractiveSession` instance MUST be created
- AND it MUST NOT reuse the previous session instance

## ADDED Requirements

### Requirement: InteractiveSessionFactory interface

- MUST be a functional interface in `org.specdriven.agent.loop`
- MUST define `InteractiveSession create(String sessionId)`
- `sessionId` MUST be non-null and non-blank
- `create()` MUST return a non-null `InteractiveSession` in `NEW` state
- `create()` MUST NOT throw checked exceptions — factory failures MUST be captured in the returned session (which enters `ERROR` state on `start()`)

### Requirement: LOOP_INTERACTIVE_ENTERED EventType

- MUST add `LOOP_INTERACTIVE_ENTERED` to the existing `EventType` enum in `org.specdriven.agent.event`
- MUST include metadata: `sessionId` (String), `questionId` (String), `changeName` (String)
- Existing EventType values MUST NOT change

### Requirement: LOOP_INTERACTIVE_EXITED EventType

- MUST add `LOOP_INTERACTIVE_EXITED` to the existing `EventType` enum in `org.specdriven.agent.event`
- MUST include metadata: `sessionId` (String), `questionId` (String), `sessionEndState` (String — "CLOSED" or "ERROR")
- Existing EventType values MUST NOT change

### Requirement: Interactive session factory backward compatibility

- All existing `DefaultLoopDriver` constructors MUST remain valid and MUST behave as if `InteractiveSessionFactory` is null
- A new constructor overload MUST accept the additional `InteractiveSessionFactory` parameter
- Code that does not pass `InteractiveSessionFactory` MUST compile and run unchanged

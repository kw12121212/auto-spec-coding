---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/interactive/InteractiveSession.java
    - src/main/java/org/specdriven/agent/interactive/InteractiveSessionState.java
    - src/main/java/org/specdriven/agent/interactive/InMemoryInteractiveSession.java
  tests:
    - src/test/java/org/specdriven/agent/interactive/InteractiveSessionTest.java
---

# Interactive Session — Delta Spec: interactive-session-interface

## ADDED Requirements

### Requirement: InteractiveSessionState enum

- The system MUST define `InteractiveSessionState` in `org.specdriven.agent.interactive`
- `InteractiveSessionState` MUST include `NEW`, `ACTIVE`, `CLOSED`, and `ERROR`
- `NEW` MUST mean the session has been created but not started
- `ACTIVE` MUST mean the session is started and able to accept input
- `CLOSED` MUST mean the session has been closed and is no longer interactive
- `ERROR` MUST mean the session encountered a terminal failure and MUST reject later input

### Requirement: InteractiveSession interface

- The system MUST define `InteractiveSession` in `org.specdriven.agent.interactive`
- `InteractiveSession` MUST define `String sessionId()`
- `InteractiveSession` MUST define `InteractiveSessionState state()`
- `InteractiveSession` MUST define `void start()`
- `InteractiveSession` MUST define `void submit(String input)`
- `InteractiveSession` MUST define `List<String> drainOutput()`
- `InteractiveSession` MUST define `void close()`
- `sessionId()` MUST return a stable non-blank identifier for the lifetime of the session
- `start()` MUST transition a session from `NEW` to `ACTIVE`
- `start()` MUST reject invocation when the session state is not `NEW`
- `submit(input)` MUST reject null or blank input
- `submit(input)` MUST reject invocation unless the session state is `ACTIVE`
- `drainOutput()` MUST return the currently available session output in emission order
- `drainOutput()` MUST clear the returned output from the pending buffer before returning
- `drainOutput()` MUST return an empty list when no output is pending
- `close()` MUST transition `NEW`, `ACTIVE`, or `ERROR` sessions to `CLOSED`
- `close()` MUST be idempotent when the session state is already `CLOSED`

#### Scenario: Newly created session is not active yet

- GIVEN a newly created `InteractiveSession`
- WHEN its state is queried before `start()`
- THEN `state()` MUST return `NEW`

#### Scenario: Start activates the session

- GIVEN a newly created `InteractiveSession`
- WHEN `start()` is called
- THEN `state()` MUST return `ACTIVE`

#### Scenario: Submit before start is rejected

- GIVEN an `InteractiveSession` whose state is `NEW`
- WHEN `submit("SHOW STATUS")` is called
- THEN the session MUST reject the call
- AND `state()` MUST remain `NEW`

#### Scenario: Blank input is rejected

- GIVEN an `InteractiveSession` whose state is `ACTIVE`
- WHEN `submit("")` or `submit("   ")` is called
- THEN the session MUST reject the call

#### Scenario: Drain output returns ordered snapshot and clears buffer

- GIVEN an `InteractiveSession` whose pending output contains multiple messages in a known order
- WHEN `drainOutput()` is called
- THEN it MUST return those messages in the same order
- AND a second immediate `drainOutput()` call MUST return an empty list

#### Scenario: Closed session rejects later input

- GIVEN an `InteractiveSession` that has been closed
- WHEN `submit("SHOW STATUS")` is called
- THEN the session MUST reject the call
- AND `state()` MUST remain `CLOSED`

#### Scenario: Close is idempotent

- GIVEN an `InteractiveSession` whose state is `CLOSED`
- WHEN `close()` is called again
- THEN no exception MUST be thrown
- AND `state()` MUST remain `CLOSED`

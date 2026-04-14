---
mapping:
  implementation:
    - pom.xml
    - src/main/java/org/specdriven/agent/interactive/LealoneAgentAdapter.java
  tests:
    - src/test/java/org/specdriven/agent/interactive/LealoneAgentAdapterTest.java
    - src/test/java/org/specdriven/agent/interactive/InteractiveSessionTest.java
---

# Interactive Session - Delta Spec: lealone-agent-adapter

## ADDED Requirements

### Requirement: Lealone-backed interactive session adapter

The system MUST provide a Lealone-backed `InteractiveSession` implementation for SQL and natural-language interactive input.

#### Scenario: Adapter starts an interactive Lealone session
- GIVEN a newly created Lealone-backed interactive session
- WHEN `start()` is called
- THEN `state()` MUST return `ACTIVE`
- AND `sessionId()` MUST return a stable non-blank identifier

#### Scenario: Adapter enters error state after start failure
- GIVEN a newly created Lealone-backed interactive session
- WHEN the configured Lealone execution surface cannot be opened
- THEN `start()` MUST be rejected
- AND `state()` MUST return `ERROR`

#### Scenario: Adapter submits input to Lealone execution
- GIVEN a Lealone-backed interactive session in `ACTIVE` state
- WHEN `submit("SHOW SERVICES")` is called
- THEN the input MUST be submitted to the configured Lealone execution surface
- AND any produced output MUST become available through `drainOutput()` in emission order

#### Scenario: Adapter preserves drain semantics
- GIVEN a Lealone-backed interactive session has produced one or more output messages
- WHEN `drainOutput()` is called
- THEN the returned messages MUST be ordered by emission
- AND a second immediate `drainOutput()` call MUST return an empty list

#### Scenario: Adapter closes Lealone resources
- GIVEN a Lealone-backed interactive session in `ACTIVE` state
- WHEN `close()` is called
- THEN the session MUST release its Lealone execution resource
- AND `state()` MUST return `CLOSED`
- AND later `submit("SHOW STATUS")` MUST be rejected

#### Scenario: Adapter enters error state after execution failure
- GIVEN a Lealone-backed interactive session in `ACTIVE` state
- WHEN the configured Lealone execution surface fails while handling submitted input
- THEN `state()` MUST return `ERROR`
- AND later input submission MUST be rejected
- AND pending output, if any, MUST remain available through `drainOutput()`

### Requirement: Lealone adapter does not route answers

The Lealone-backed adapter MUST NOT submit answers to waiting questions or resume loop execution by itself.

#### Scenario: Adapter remains a session surface only
- GIVEN a Lealone-backed interactive session is used to submit input
- WHEN the input is accepted by the adapter
- THEN the adapter MUST limit its observable responsibility to interactive session output and lifecycle state
- AND Question/Answer routing MUST remain governed by the existing question resolution components
- AND loop pause/resume behavior MUST remain governed by the existing loop components

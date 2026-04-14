---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/event/EventType.java
  tests:
    - src/test/java/org/specdriven/agent/loop/DefaultLoopDriverTest.java
---

## ADDED Requirements

### Requirement: LOOP_INTERACTIVE_ENTERED and LOOP_INTERACTIVE_EXITED event types

- MUST add `LOOP_INTERACTIVE_ENTERED` to the existing `EventType` enum in `org.specdriven.agent.event`
- MUST add `LOOP_INTERACTIVE_EXITED` to the existing `EventType` enum in `org.specdriven.agent.event`
- Existing EventType values MUST NOT change

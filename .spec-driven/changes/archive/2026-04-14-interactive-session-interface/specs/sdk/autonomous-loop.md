---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/interactive/InteractiveSession.java
    - src/main/java/org/specdriven/agent/loop/DefaultLoopDriver.java
    - src/main/java/org/specdriven/agent/loop/LoopDriver.java
  tests:
    - src/test/java/org/specdriven/agent/interactive/InteractiveSessionTest.java
    - src/test/java/org/specdriven/agent/loop/DefaultLoopDriverTest.java
---

# Autonomous Loop Driver — Delta Spec: interactive-session-interface

## ADDED Requirements

### Requirement: Interactive session boundary for later human-in-loop integration

- The autonomous loop integration surface MUST treat interactive human handling as a separate session contract rather than as direct `DefaultLoopDriver`-specific methods
- The first M29 contract change MUST define this boundary without requiring `DefaultLoopDriver` to enter interactive mode yet
- Later interactive bridge work MAY depend on `InteractiveSession`, but this change MUST NOT require any new `LoopDriver` public methods

#### Scenario: Existing loop public API remains unchanged

- GIVEN code that already integrates with `LoopDriver`
- WHEN the interactive session contract is introduced
- THEN existing `LoopDriver` start, pause, resume, stop, and iteration query methods MUST remain sufficient to compile unchanged callers

#### Scenario: Interactive contract can be layered onto paused loop handling later

- GIVEN a loop iteration paused because of a human-escalated question
- WHEN a later roadmap change bridges loop escalation into interactive mode
- THEN that bridge MUST be able to depend on `InteractiveSession` as the session boundary
- AND this change MUST NOT predefine the later bridge's runtime behavior beyond that contract dependency

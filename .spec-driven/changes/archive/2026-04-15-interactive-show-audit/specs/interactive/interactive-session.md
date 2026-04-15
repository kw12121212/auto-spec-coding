---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/interactive/InteractiveCommandHandler.java
    - src/main/java/org/specdriven/agent/loop/SequentialMilestoneScheduler.java
    - src/main/java/org/specdriven/agent/question/QuestionRuntime.java
  tests:
    - src/test/java/org/specdriven/agent/interactive/InteractiveCommandHandlerTest.java
    - src/test/java/org/specdriven/agent/loop/SequentialMilestoneSchedulerTest.java
---

## MODIFIED Requirements

### Requirement: InteractiveCommandHandler
Previously: For `ShowCommand`:
- MUST format a human-readable status summary and enqueue it to the session output via `session` (delegating to internal query methods)
- `ShowType.SERVICES` MUST format currently registered services
- `ShowType.STATUS` MUST format the current loop and question status
- `ShowType.ROADMAP` MUST format the current roadmap progress summary

For `ShowCommand`:
- MUST format a human-readable summary and enqueue it to the session output via `session` (delegating to internal query methods)
- `ShowType.SERVICES` MUST report the services, capabilities, or subsystems relevant to the paused human-in-the-loop session
- `ShowType.SERVICES` MUST NOT degrade into a placeholder string once the interactive command handler is configured for normal use
- `ShowType.STATUS` MUST report the current waiting-question and interactive session status available to the command handler without changing loop control state
- `ShowType.STATUS` MUST NOT degrade into a placeholder string once the interactive command handler is configured for normal use
- `ShowType.ROADMAP` MUST report roadmap progress derived from the repository's on-disk roadmap files using the canonical milestone and planned-change structure
- `ShowType.ROADMAP` MUST NOT degrade into a placeholder string once the interactive command handler is configured for normal use

#### Scenario: SHOW SERVICES reports paused-session capabilities

- GIVEN an active interactive session with a waiting human-handled question
- WHEN `handle(ShowCommand(SERVICES), question)` is called
- THEN the output available through `session.drainOutput()` MUST describe the services, capabilities, or subsystems relevant to that paused session
- AND the output MUST be non-blank
- AND the output MUST NOT equal a placeholder-only message

#### Scenario: SHOW STATUS reports waiting question context

- GIVEN an active interactive session with a waiting question
- WHEN `handle(ShowCommand(STATUS), question)` is called
- THEN the output available through `session.drainOutput()` MUST describe the waiting question or interactive status context
- AND the output MUST be non-blank
- AND the output MUST NOT equal a placeholder-only message

#### Scenario: SHOW ROADMAP reports roadmap progress from disk

- GIVEN an interactive command handler configured against a repository roadmap
- WHEN `handle(ShowCommand(ROADMAP), question)` is called
- THEN the output available through `session.drainOutput()` MUST summarize roadmap progress using milestone and planned-change data from disk
- AND the output MUST be non-blank
- AND the output MUST NOT equal a placeholder-only message

---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/event/EventType.java
  tests:
    - src/test/java/org/specdriven/agent/event/EventSystemTest.java
---

# Event System Spec

## MODIFIED Requirements

### Requirement: EventType enum

- MUST preserve all previously required event types
- MUST additionally define `LLM_CONFIG_CHANGED`

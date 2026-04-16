---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/event/EventType.java
  tests: []
---

## MODIFIED Requirements

### Requirement: EventType enum

Previously: The enum MUST define (among others): `SKILL_HOT_LOAD_OPERATION`, `INTERACTIVE_COMMAND_HANDLED`.

The enum MUST additionally define: `PLATFORM_HEALTH_CHECKED`, `PLATFORM_METRICS_SNAPSHOT`.

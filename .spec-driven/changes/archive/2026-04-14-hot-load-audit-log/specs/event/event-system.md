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
- MUST additionally define `SKILL_HOT_LOAD_OPERATION`

## ADDED Requirements

### Requirement: Skill hot-load audit event metadata

- `SKILL_HOT_LOAD_OPERATION` events MUST use metadata values that satisfy the existing event JSON serialization constraints
- `SKILL_HOT_LOAD_OPERATION` metadata MUST identify the requested hot-load operation using one of `load`, `replace`, or `unload`
- `SKILL_HOT_LOAD_OPERATION` metadata MUST include the skill name
- `SKILL_HOT_LOAD_OPERATION` metadata MUST include the operation result using a stable string value
- `SKILL_HOT_LOAD_OPERATION` metadata for source-bearing `load` and `replace` operations MUST include the source hash
- `SKILL_HOT_LOAD_OPERATION` metadata MUST NOT include raw Java source text
- `SKILL_HOT_LOAD_OPERATION` metadata SHOULD include requester information when a requester is available from the caller context
- Failed or rejected `SKILL_HOT_LOAD_OPERATION` metadata MUST include a stable failure category

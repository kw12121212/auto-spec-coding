---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/executor/SkillServiceExecutorFactory.java
  tests:
    - src/test/java/org/specdriven/skill/executor/SkillServiceExecutorFactoryTest.java
---

# skill-executor-plugin.md - delta for hot-load-default-disabled

## MODIFIED Requirements

### Requirement: SkillServiceExecutorFactory

- When a configured `SkillHotLoader` does not expose an active loader for the service's skill name, including the default-disabled activation state, `createServiceExecutor(Service service)` MUST preserve the existing default executor creation behavior

## ADDED Scenarios

#### Scenario: disabled hot-loader still falls back to default behavior

- GIVEN `SkillServiceExecutorFactory` is constructed with a configured `SkillHotLoader`
- AND that hot-loader has activation disabled and therefore exposes no active loader for the service's skill name
- WHEN `createServiceExecutor(service)` is called
- THEN the factory MUST return the same default executor type as before this change

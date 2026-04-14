---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/executor/SkillServiceExecutor.java
    - src/main/java/org/specdriven/skill/executor/SkillServiceExecutorFactory.java
  tests:
    - src/test/java/org/specdriven/skill/executor/SkillServiceExecutorFactoryTest.java
---

# skill-executor-plugin.md - delta for hot-load-integration

## ADDED Requirements

### Requirement: SkillServiceExecutorFactory hot-loader preference

- `SkillServiceExecutorFactory` MUST support construction with an optional
  `SkillHotLoader`
- When the factory creates an executor for a service whose skill name has an active
  loader in the configured `SkillHotLoader`, it MUST load the executor class named by
  `service.getImplementBy()` from that hot-loaded `ClassLoader`
- The instantiated hot-loaded executor class MUST implement `ServiceExecutor`
- When the factory is constructed without a `SkillHotLoader`, or when no active loader
  exists for the service's skill name, it MUST preserve the current executor creation
  behavior

## ADDED Scenarios

#### Scenario: hot-loaded executor class is preferred over default instantiation

- GIVEN a configured `SkillHotLoader` has an active loader for the service's skill
- WHEN `createServiceExecutor(service)` is called
- THEN the factory MUST instantiate the executor class from the hot-loaded
  `ClassLoader`

#### Scenario: factory without hot-loader preserves current behavior

- GIVEN `SkillServiceExecutorFactory` is constructed without a `SkillHotLoader`
- WHEN `createServiceExecutor(service)` is called
- THEN the factory MUST return the same default executor type as before this change

#### Scenario: absent hot-loaded class falls back to default behavior

- GIVEN a configured `SkillHotLoader` does not have an active loader for the service's
  skill name
- WHEN `createServiceExecutor(service)` is called
- THEN the factory MUST fall back to the existing executor creation path

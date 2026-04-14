---
mapping:
  implementation:
    - src/main/java/org/specdriven/skill/executor/SkillParameterParser.java
    - src/main/java/org/specdriven/skill/executor/SkillServiceExecutor.java
    - src/main/java/org/specdriven/skill/executor/SkillServiceExecutorFactory.java
    - src/main/java/org/specdriven/skill/sql/SkillSqlException.java
    - src/main/java/org/specdriven/skill/store/FileSystemInstructionStore.java
    - src/main/resources/META-INF/services/com.lealone.db.service.ServiceExecutorFactory
  tests:
    - src/test/java/org/specdriven/skill/executor/SkillParameterParserTest.java
    - src/test/java/org/specdriven/skill/executor/SkillServiceExecutorFactoryTest.java
    - src/test/java/org/specdriven/skill/executor/SkillServiceExecutorTest.java
---

# skill-executor-plugin.md

## ADDED Requirements

### Requirement: SkillServiceExecutorFactory

- MUST extend `ServiceExecutorFactoryBase` and implement `ServiceExecutorFactory`
- MUST be named `"skill"` — this name MUST match the `LANGUAGE` value in the CREATE SERVICE SQL
- MUST support construction with an optional `SkillHotLoader`
- MUST implement `createServiceExecutor(Service service)` returning a `ServiceExecutor`
  for the given `Service`
- When the configured `SkillHotLoader` has an active loader for the service's skill
  name, `createServiceExecutor(Service service)` MUST load the executor class named by
  `service.getImplementBy()` from that hot-loaded `ClassLoader`
- The instantiated hot-loaded executor MUST implement `ServiceExecutor`
- When no `SkillHotLoader` is configured, or when no active loader exists for the
  service's skill name, `createServiceExecutor(Service service)` MUST preserve the
  existing default executor creation behavior
- The same default executor fallback MUST apply when a configured `SkillHotLoader` remains in the default-disabled activation state and therefore exposes no active loader
- MUST be registered via `META-INF/services/com.lealone.db.service.ServiceExecutorFactory`
- MUST be in the `org.specdriven.skill.executor` package

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

#### Scenario: disabled hot-loader still falls back to default behavior

- GIVEN `SkillServiceExecutorFactory` is constructed with a configured `SkillHotLoader`
- AND that hot-loader has activation disabled and therefore exposes no active loader for the service's skill name
- WHEN `createServiceExecutor(service)` is called
- THEN the factory MUST return the same default executor type as before this change

### Requirement: SkillServiceExecutor

- MUST implement `ServiceExecutor`
- MUST extract `skill_id` (String) and `skill_dir` (Path) by parsing the `PARAMETERS (...)` block in `service.getCreateSQL()`
- MUST throw `SkillSqlException` if `skill_id` or `skill_dir` is missing from PARAMETERS
- MUST implement `executeService(String methodName, Map<String,Object> methodArgs)` for the `"EXECUTE"` method
- `executeService` MUST load the instruction body via `FileSystemInstructionStore.loadInstructions(skillId, skillDir)` and use it as the system prompt
- `executeService` MUST extract the user prompt from `methodArgs` under the key `"PROMPT"`
- `executeService` MUST build a `Conversation` and run the agent loop via `DefaultOrchestrator` using an `LlmClient` from the configured provider
- `executeService` MUST return the content of the final `AssistantMessage` from the conversation, or an empty string if the conversation ends without a text response
- `executeService` MUST throw `SkillInstructionStoreException` if the instruction body cannot be loaded
- MUST be in the `org.specdriven.skill.executor` package

### Requirement: PARAMETERS parsing

- The executor MUST parse `skill_id` and `skill_dir` from the SQL `PARAMETERS` clause in `service.getCreateSQL()`
- Parsing MUST accept both `PARAMETERS (key='value', ...)` syntax and the quoted-pair format emitted by `SkillSqlConverter`
- Parsing MUST handle single-quote-escaped values (doubled single quotes `''` → `'`)
- Parsing MUST be case-insensitive for key names

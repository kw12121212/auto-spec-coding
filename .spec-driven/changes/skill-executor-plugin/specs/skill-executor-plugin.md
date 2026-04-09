# skill-executor-plugin

## ADDED Requirements

### Requirement: SkillServiceExecutorFactory

- MUST extend `ServiceExecutorFactoryBase` and implement `ServiceExecutorFactory`
- MUST be named `"skill"` — this name MUST match the `LANGUAGE` value in the CREATE SERVICE SQL
- MUST implement `createServiceExecutor(Service service)` returning a `SkillServiceExecutor` initialized with the given `Service`
- MUST be registered via `META-INF/services/com.lealone.db.service.ServiceExecutorFactory`
- MUST be in the `org.specdriven.skill.executor` package

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

- The executor MUST parse `skill_id` and `skill_dir` from the SQL `PARAMETERS (key='value', ...)` block
- Parsing MUST handle single-quote-escaped values (doubled single quotes `''` → `'`)
- Parsing MUST be case-insensitive for key names

## MODIFIED Requirements

### Requirement: SkillSqlConverter LANGUAGE clause (modifies skill-sql-converter.md)

- MUST emit `LANGUAGE 'skill'` instead of `LANGUAGE 'java'`
- All other SQL generation requirements remain unchanged

# Tasks: skill-executor-plugin

## Implementation

- [ ] Create `SkillParameterParser` (package-private) in `org.specdriven.skill.executor` — parses `skill_id` and `skill_dir` from a `PARAMETERS (...)` SQL block extracted from `service.getCreateSQL()`
- [ ] Create `SkillServiceExecutor implements ServiceExecutor` in `org.specdriven.skill.executor` — stores `Service` reference, lazy-initializes metadata via `SkillParameterParser`, implements `executeService(String methodName, Map<String,Object> methodArgs)` to run the agent loop and return the final text response
- [ ] Create `SkillServiceExecutorFactory extends ServiceExecutorFactoryBase` in `org.specdriven.skill.executor` with name `"skill"`, implementing `createServiceExecutor(Service service)` returning a new `SkillServiceExecutor`
- [ ] Add SPI file `src/main/resources/META-INF/services/com.lealone.db.service.ServiceExecutorFactory` listing `org.specdriven.skill.executor.SkillServiceExecutorFactory`
- [ ] Update `SkillSqlConverter` to emit `LANGUAGE 'skill'` instead of `LANGUAGE 'java'`; update the LANGUAGE requirement in the change's delta spec

## Testing

- [ ] Run `mvn compile` to build and verify no compilation errors
- [ ] Run `mvn test -Dtest="SkillParameterParserTest,SkillServiceExecutorTest,SkillServiceExecutorFactoryTest"` for targeted unit tests covering: SQL PARAMETERS extraction for all fields, executor invocation with a stub `LlmClient`, factory creates executor instance, executor returns last assistant text
- [ ] Run `mvn test -Dtest="SkillSqlConverterTest"` to verify the LANGUAGE field change does not break existing converter tests

## Verification

- [ ] Verify implementation matches proposal

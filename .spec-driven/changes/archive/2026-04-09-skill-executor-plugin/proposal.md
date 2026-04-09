# skill-executor-plugin

## What

Implement `SkillServiceExecutorFactory` — a Lealone `ServiceExecutorFactory` SPI plugin that creates an agent-loop executor for every skill service registered via `SkillAutoDiscovery`. When Lealone invokes the skill service's `execute(prompt varchar) varchar` method, the executor loads the instruction body from `FileSystemInstructionStore`, builds an `AgentContext` with M2 Tool instances, and runs `DefaultOrchestrator` against an `LlmClient` to produce the text response.

## Why

The three completed M11 components (sql-schema, instructions-store, auto-discovery) make skills registerable but not executable. The executor plugin is the last piece: it gives every registered skill a live agent loop invocable through Lealone's `CREATE SERVICE` SQL interface, fulfilling M11's core goal of "通过插件 SPI 扩展实现 skill 执行引擎".

## Scope

In scope:
- `SkillServiceExecutorFactory extends ServiceExecutorFactoryBase` in `org.specdriven.skill.executor`, registered under name `"skill"` via `META-INF/services/com.lealone.db.service.ServiceExecutorFactory`
- `SkillServiceExecutor implements ServiceExecutor` — parses `skill_id` and `skill_dir` from the Service's PARAMETERS block in `service.getCreateSQL()`, loads instructions via `FileSystemInstructionStore`, runs the orchestrator loop
- SPI registration file under `src/main/resources/META-INF/services/`
- Unit tests for the factory and executor (PARAMETERS parsing, instruction loading, agent loop invocation with a mock `LlmClient`)
- Updating `SkillSqlConverter` to emit `LANGUAGE 'skill'` instead of `LANGUAGE 'java'` so Lealone routes to our factory (and updating its spec delta accordingly)

Out of scope:
- Lealone core modifications
- Changes to HTTP or JSON-RPC interfaces
- LLM provider configuration infrastructure (reuses `DefaultLlmProviderRegistry`)
- Skill CLI Java rewrite (`skill-cli-java` — separate planned change)
- Per-skill executor class generation

## Unchanged Behavior

- `SkillMarkdownParser`, `SkillSqlConverter` output format (except the LANGUAGE value), `SkillAutoDiscovery`, and `FileSystemInstructionStore` behavior must not change
- Existing `DefaultAgent`, `DefaultOrchestrator`, `OrchestratorConfig`, and M2 Tool implementations must not be modified

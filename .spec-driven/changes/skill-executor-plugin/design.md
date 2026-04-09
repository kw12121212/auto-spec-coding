# Design: skill-executor-plugin

## Approach

### Factory registration

`SkillServiceExecutorFactory` extends `ServiceExecutorFactoryBase` and is registered with name `"skill"`. Lealone maps the SQL `LANGUAGE` value to the factory name in its `PluginManager`, so services declared with `LANGUAGE 'skill'` are routed to our factory. This requires `SkillSqlConverter` to emit `LANGUAGE 'skill'` (currently `'java'`).

The SPI entry in `META-INF/services/com.lealone.db.service.ServiceExecutorFactory` points to `org.specdriven.skill.executor.SkillServiceExecutorFactory`.

### Executor creation

`createServiceExecutor(Service service)` returns a `SkillServiceExecutor(service)`. The executor is lazy — it parses metadata and loads instructions on the first `executeService` call.

### PARAMETERS parsing

Lealone's `Service` does not expose a parsed PARAMETERS map. The executor parses `service.getCreateSQL()` to extract `skill_id` and `skill_dir` using a simple regex over the `PARAMETERS (...)` block. Since `SkillSqlConverter` controls the format, the pattern is deterministic.

### Agent loop execution

On `executeService("EXECUTE", Map<String,Object> methodArgs)`:

1. Parse `skill_id` and `skill_dir` (once, cached after first call)
2. Load instructions: `FileSystemInstructionStore.loadInstructions(skillId, skillDir)` — returns the SKILL.md instruction body used as the system prompt
3. Build `Conversation`: append a `UserMessage` from `methodArgs.get("PROMPT")`
4. Build a tool registry (`Map<String, Tool>`) with the default M2 tool set (Bash, Read, Write, Edit, Glob, Grep) keyed by tool name
5. Build `SimpleAgentContext` with the conversation, tool registry, `skill_dir` as `workDir`, and `skill_id` in the config map
6. Create `LlmClient` from `DefaultLlmProviderRegistry` default provider
7. Instantiate `DefaultAgent`, call `init/start/execute/stop` with a synthetic running state
8. Return the last `AssistantMessage` content from the conversation; return `""` if no text response

### Package layout

```
org.specdriven.skill.executor/
  SkillServiceExecutorFactory.java   (ServiceExecutorFactoryBase)
  SkillServiceExecutor.java          (ServiceExecutor)
  SkillParameterParser.java          (package-private, parses PARAMETERS from SQL string)
```

## Key Decisions

- **`LANGUAGE 'skill'`**: Using `LANGUAGE 'java'` requires the `IMPLEMENT BY` class to exist on the classpath, which breaks for dynamically discovered skills. `LANGUAGE 'skill'` lets our factory intercept all skill services without code generation.
- **Parse PARAMETERS from SQL string**: No parsed map API on `Service`. The format is owned by `SkillSqlConverter` so regex parsing is safe and stable.
- **Reuse `DefaultOrchestrator`**: The existing orchestrator already implements the receive→think→act→observe loop with hooks and turn limits. No new loop logic is needed.
- **Default tool set = all M2 tools**: Until `allowed_tools` is available in PARAMETERS, all M2 tools are bound. This is safe because the permission hook in `OrchestratorConfig` enforces policy at runtime.

## Alternatives Considered

- **`LANGUAGE 'java'` + single generic `SkillAgentExecutor` IMPLEMENT BY class**: Would work if all skills used the same class name, but it conflicts with the existing spec contract and ties the executor to a fixed class name rather than a factory.
- **Code generation via `supportsGenCode()`**: Lealone's `genCode` SPI could emit per-skill Java source. This is significantly more complex and buys nothing for our use case — a single generic executor with PARAMETERS reading is sufficient.
- **Read SKILL.md again at execution time for `allowed_tools`**: Possible, but creates a second SKILL.md parse on every call. Better resolved as a PARAMETERS field if needed.

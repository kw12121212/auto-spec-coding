# Questions: skill-executor-plugin

## Open

## Resolved

- [x] Q: Does this change scope include updating `SkillSqlConverter` to emit `LANGUAGE 'skill'` instead of `LANGUAGE 'java'`?
  Context: Lealone maps the LANGUAGE value to the `ServiceExecutorFactory` plugin name. With `LANGUAGE 'java'`, Lealone uses `JavaServiceExecutorFactory` and tries to instantiate the `IMPLEMENT BY` class directly via reflection — which doesn't exist for dynamic skills. With `LANGUAGE 'skill'`, Lealone routes to `SkillServiceExecutorFactory`. This is a modification to an already-completed change (`skill-sql-schema`) and its tests.
  A: Yes — change to `LANGUAGE 'skill'`. Required for correctness and already in proposal scope.

- [x] Q: How should `allowed_tools` be sourced for tool binding?
  Context: M11 specifies "绑定 allowed-tools 到 M1 Tool 实例" but the current `SkillSqlConverter` does not store `allowed_tools` in PARAMETERS, and `SkillFrontmatter` does not include this field.
  A: Option (c) — bind all M2 tools by default. The permission hook in `OrchestratorConfig` enforces policy at runtime. `allowed_tools` support can be added in a follow-up.

- [x] Q: How is the `LlmProvider` obtained by the executor?
  Context: The executor needs an `LlmClient`. `SkillServiceExecutorFactory` is instantiated by Lealone's SPI loader, not by the SDK's own assembly.
  A: Use `DefaultLlmProviderRegistry` as a lazily-initialized static singleton, reading LLM config from environment variables (`ANTHROPIC_API_KEY`, `OPENAI_API_KEY`) or a well-known config file path on first call. Fail fast with a clear error if neither is present.

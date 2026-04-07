# Tasks: llm-provider-registry

## Implementation

- [x] Create `LlmProviderRegistry` interface in `org.specdriven.agent.agent` with methods: `register(String name, LlmProvider)`, `provider(String name)`, `defaultProvider()`, `providerNames()`, `remove(String name)`, `setDefault(String name)`, `route(String skillName)`, `close()`
- [x] Create `SkillRoute` record in `org.specdriven.agent.agent` with `providerName` (String) and `modelOverride` (String, nullable)
- [x] Create `LlmProviderFactory` functional interface in `org.specdriven.agent.agent` with `LlmProvider create(LlmConfig config)` method
- [x] Implement `DefaultLlmProviderRegistry` with `ConcurrentHashMap` for providers, `ConcurrentHashMap` for skill routing, and `defaultProviderName` field
- [x] Implement `register()` — validate name/provider non-null, reject duplicate names with `IllegalArgumentException`, store in map
- [x] Implement `provider()` — lookup by name, throw `IllegalArgumentException` if not found
- [x] Implement `defaultProvider()` — return provider for `defaultProviderName`, or first registered provider if default not set, or throw if registry empty
- [x] Implement `route()` — check skill routing map, return `SkillRoute` or null
- [x] Implement `setDefault()` — validate provider exists, set `defaultProviderName`
- [x] Implement `remove()` — remove provider, clear `defaultProviderName` if it was the removed provider
- [x] Implement `close()` — close all registered providers, clear maps
- [x] Implement static factory `fromConfig(Config, Map<String, LlmProviderFactory>)` — parse `llm.providers` section, instantiate via factories, set default, parse `skill-routing`
- [x] Add delta spec to `changes/llm-provider-registry/specs/llm-provider.md`

## Testing

- [x] Run `mvn compile` to verify no compilation errors
- [x] Create `LlmProviderRegistryTest` — test register, lookup, remove, duplicate rejection, empty registry behavior
- [x] Create `DefaultLlmProviderRegistryTest` — test default provider fallback, setDefault validation, first-registered fallback
- [x] Create `SkillRouteTest` — test record construction and accessors
- [x] Test skill routing — register routing entries, verify route returns correct SkillRoute, verify null for unknown skill
- [x] Test close cascades — verify all providers are closed when registry closes
- [x] Test fromConfig — parse YAML config, verify providers registered, default set, skill routing populated
- [x] Test thread safety — concurrent register/lookup from multiple threads
- [x] Run unit tests: `mvn test -pl . -Dtest="org.specdriven.agent.agent.LlmProvider*Test,org.specdriven.agent.agent.SkillRouteTest"`

## Verification

- [x] Verify all delta spec requirements have corresponding test coverage
- [x] Verify `LlmProviderRegistry` interface methods match spec exactly
- [x] Verify no modifications to existing `LlmProvider`, `LlmConfig`, `LlmClient`, `LlmResponse` types
- [x] Run `mvn test` passes with zero failures

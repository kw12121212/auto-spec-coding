# Design: llm-provider-registry

## Approach

Introduce a `LlmProviderRegistry` interface and `DefaultLlmProviderRegistry` concrete class in `org.specdriven.agent.agent`. The registry holds named `LlmProvider` instances in a `ConcurrentHashMap`. A `defaultProviderName` field tracks the fallback. A `skillRouting` map (`Map<String, SkillRoute>`) maps skill names to a `(providerName, modelOverride)` pair. A static factory `fromConfig(Config)` creates and registers providers from the YAML config structure.

### Configuration format (YAML)

```yaml
llm:
  providers:
    openai-main:
      baseUrl: "https://api.openai.com/v1"
      apiKey: "${OPENAI_API_KEY}"
      model: "gpt-4"
    deepseek:
      baseUrl: "https://api.deepseek.com/v1"
      apiKey: "${DEEPSEEK_API_KEY}"
      model: "deepseek-chat"
    claude:
      baseUrl: "https://api.anthropic.com"
      apiKey: "${ANTHROPIC_API_KEY}"
      model: "claude-sonnet-4-6-20250514"
  default: "openai-main"
  skill-routing:
    code-review: { provider: "claude", model: "claude-opus-4-6-20250514" }
    code-gen: { provider: "deepseek" }
```

Note: concrete provider instantiation (OpenAI vs Claude) is deferred to the provider implementation changes. The registry factory will require a provider factory or SPI mechanism. For now, `fromConfig` registers pre-built `LlmProvider` instances; the factory wiring is part of each provider change.

## Key Decisions

1. **Interface + default implementation** — `LlmProviderRegistry` as an interface allows test mocks and alternative implementations (e.g., DB-backed in the future).

2. **ConcurrentHashMap for providers** — providers may be registered/looked up from different threads (e.g., during agent orchestration). No global lock needed for read-heavy access.

3. **SkillRoute as a record** — `SkillRoute(String providerName, String modelOverride)` is immutable and explicit. `modelOverride` is nullable; when set, it overrides the provider's default model for that skill.

4. **fromConfig uses provider factory map** — `fromConfig(Config, Map<String, LlmProviderFactory>)` accepts a map of factory name to factory instance. Each provider change (openai, claude) registers its own factory. This decouples registry from concrete providers.

5. **Close cascades** — closing the registry closes all registered providers, preventing resource leaks.

## Alternatives Considered

1. **ServiceLoader SPI** — Use `java.util.ServiceLoader` to auto-discover providers. Rejected for now: adds classpath complexity; the explicit factory map is simpler and sufficient for 2-3 providers. Can add SPI later without breaking the interface.

2. **Registry as singleton** — Global static registry. Rejected: makes testing harder, prevents multiple registry instances (e.g., per-tenant in future).

3. **Routing via LlmRequest only** — Let callers always specify the provider name in the request, no registry-level routing. Rejected: skill-based routing is an explicit M5 requirement and centralizing it avoids duplicating routing logic in every caller.

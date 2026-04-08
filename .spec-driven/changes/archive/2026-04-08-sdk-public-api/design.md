# Design: sdk-public-api

## Approach

### Single facade entry point with builder

`SpecDriven` is the sole public entry class. Users interact exclusively with it and the types it returns (`SdkAgent`). Internally, `SpecDriven` holds references to the internal components (DefaultAgent, LlmProviderRegistry, Config, etc.) but never exposes them directly.

```
User → SpecDriven.builder() → SdkBuilder → build() → SpecDriven instance
                                                              ↓
                                                     createAgent() → SdkAgent
                                                              ↓
                                                     run(prompt) → String
```

### Package separation

All public SDK types live in `org.specdriven.sdk`. Internal types remain in `org.specdriven.agent.*`. The SDK package re-exports shared value types (Tool, ToolResult, ToolInput, Message subtypes) by referencing them from their original packages — no duplication.

### Auto-assembly with manual override

The builder provides both:
1. `.config(Path)` — auto-assembles LLM providers, vault, permissions from YAML
2. `.providerRegistry(LlmProviderRegistry)` — manual injection for advanced users

When both are set, manual injection takes precedence.

### SdkAgent as agent handle

`SdkAgent` wraps an internal `DefaultAgent` and manages its full lifecycle (init → start → execute → stop → close). Users never interact with `Agent` directly. `SdkAgent.run(String)` is a convenience that creates a Conversation, appends a UserMessage, and delegates to the orchestrator loop.

## Key Decisions

1. **`SpecDriven` is not a singleton** — users create instances via builder. Multiple SDK instances can coexist (e.g., different configs for different providers).

2. **`SdkAgent` owns the lifecycle** — calling `run()` auto-manages init/start/execute/stop. Users can also use `start()` + `run()` + `stop()` for explicit control.

3. **No re-export wrapper types** — `Tool`, `ToolResult`, `ToolInput`, `Message` are used from their original packages. The SDK facade does not wrap them.

4. **Config auto-assembly is opt-in** — calling `.config(Path)` triggers auto-assembly; calling only `.providerRegistry()` skips it. This avoids magic when users want full control.

5. **`SdkException` wraps all internal exceptions** — `ConfigException`, `VaultException`, `IllegalStateException` from agent lifecycle are all caught and re-thrown as `SdkException` with the original as cause.

## Alternatives Considered

- **Multi-entry classes (AgentBuilder, ProviderSetup, etc.)** — Rejected. M12 milestone explicitly calls for a "facade layer". Multiple entry points increase discovery cost. The builder pattern on a single class provides the same flexibility.

- **SDK types in `org.specdriven.agent` package** — Rejected. No public/internal boundary. Third-party users may accidentally depend on internal classes. A dedicated `org.specdriven.sdk` package makes the public contract explicit.

- **Wrapper types for Tool/ToolResult/Message** — Rejected. These are already public-facing interfaces, not internal implementation details. Wrapping adds complexity without benefit. Users who register custom Tools work with the original types directly.

# llm-provider-registry

## What

Implement a multi-provider registry that manages named `LlmProvider` instances, supports default provider fallback, and enables skill-to-provider routing. The registry is the central lookup point that the orchestrator and other components use to obtain `LlmClient` instances.

## Why

The `llm-provider-interface` change established the `LlmProvider`, `LlmConfig`, `LlmRequest`, and `LlmClient` types, but there is no mechanism to register, look up, or route between multiple providers. The M5 milestone requires: multiple providers registered simultaneously, runtime selection by name, default fallback, and skill-based routing. This registry is the prerequisite for both `llm-provider-openai` and `llm-provider-claude` to register themselves.

## Scope

**In scope:**
- `LlmProviderRegistry` interface — register, lookup, list, remove providers
- `DefaultLlmProviderRegistry` implementation — thread-safe, concurrent-friendly
- Default provider designation and fallback when no provider name is specified
- Skill-to-provider routing — map skill/function names to a specific provider + model override
- Configuration loading from `Config` — `providers` section with named instances, `default` key, and `skill-routing` map
- Close all registered providers when registry closes

**Out of scope:**
- Concrete provider implementations (OpenAI, Claude) — separate changes
- Streaming or token counting (M19)
- Lealone DB-backed persistence (session store is M4 `agent-session-store`)
- HTTP client implementation (leverage lealone-net in provider changes)

## Unchanged Behavior

- Existing `LlmProvider`, `LlmConfig`, `LlmRequest`, `LlmClient`, `LlmResponse`, `LlmUsage`, `ToolSchema`, `LlmStreamCallback` types and their tests remain unchanged
- `DefaultOrchestrator` continues to accept `LlmClient` directly — registry integration is additive
- `DefaultAgent.doExecute` is not modified in this change

# llm-provider-openai

## What

Implement an OpenAI-compatible LLM provider that connects to any OpenAI Chat Completions API endpoint (api.openai.com or compatible services like DeepSeek, Moonshot, GLM, etc.). Provides a concrete `LlmProvider` + `LlmClient` implementation with request serialization, response parsing (including tool calls), retry logic, and error handling.

## Why

M5's interface (`LlmProvider`, `LlmClient`, `LlmConfig`, `LlmRequest`/`LlmResponse`) and registry (`DefaultLlmProviderRegistry`) are complete but no concrete provider exists yet. Without a real provider, M4's orchestrator can only use mock LLMs, and M19 (streaming/token management) cannot be built. The OpenAI-compatible format is the industry standard — one implementation covers the majority of LLM backends.

## Scope

**In scope:**
- `OpenAiProvider` implementing `LlmProvider` — creates clients, holds config, manages lifecycle
- `OpenAiClient` implementing `LlmClient` — serializes `LlmRequest` to OpenAI Chat Completions JSON, sends HTTP request, parses response into `LlmResponse`
- Request serialization: convert `Message` subtypes to OpenAI message format, `ToolSchema` to OpenAI tools format, handle system prompt, temperature, max_tokens
- Response parsing: handle text responses and tool_call responses, map `usage` to `LlmUsage`, map `finish_reason`
- Retry with exponential backoff on transient HTTP errors (429, 500, 502, 503, 504)
- Error handling: wrap HTTP/network errors into descriptive runtime exceptions
- `OpenAiProviderFactory` implementing `LlmProviderFactory` for registry integration
- Unit tests using a mock HTTP server (e.g. `com.sun.net.httpserver.HttpServer`)

**Out of scope:**
- Streaming responses (M19)
- Token counting / context window management (M19)
- Claude Messages API provider (separate change `llm-provider-claude`)
- LLM provider registry changes (already complete)
- Interface changes to `LlmClient`, `LlmProvider`, etc.

## Unchanged Behavior

- Existing `LlmClient`, `LlmProvider`, `LlmProviderRegistry`, `LlmRequest`, `LlmResponse` interfaces and types MUST NOT change
- `DefaultLlmProviderRegistry.fromConfig()` integration point already expects a factory keyed by type name — the `"openai"` factory key will be provided by this change
- Existing tests for registry, config, and other types MUST continue to pass

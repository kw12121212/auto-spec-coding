# llm-provider-claude

## What

Implement the Anthropic Claude Messages API provider — `ClaudeProvider`, `ClaudeClient`, and `ClaudeProviderFactory` — so the LLM provider registry supports both OpenAI-compatible and Claude backends side by side.

## Why

M5's interface layer and registry are complete, and the OpenAI-compatible provider (`llm-provider-openai`) is in active development. `llm-provider-claude` is the single remaining change to complete M5. Without it, the system cannot call Claude models (claude-opus-4-6, claude-sonnet-4-6, etc.), which are the primary models used by the spec-coding-sdk lineage this project is reimplementing. Completing M5 enables M4's orchestrator to run end-to-end with real LLM backends, and unblocks M19 (streaming/token management).

## Scope

**In scope:**
- `ClaudeProvider` implementing `LlmProvider` — holds `LlmConfig`, creates `ClaudeClient` instances, no-op `close()`
- `ClaudeClient` implementing `LlmClient` — serializes `LlmRequest` to Claude Messages API JSON format, POSTs to `{baseUrl}/messages`, parses response into `LlmResponse`
- Request serialization to Claude format: system prompt as top-level `system` field; messages as role/content pairs (no `system` role in messages array); tools as `{"name", "description", "input_schema"}` (not OpenAI's `type: function` wrapper); `ToolMessage` mapped to `user` role with `tool_result` content block
- Response parsing from Claude format: `content` array blocks (`type: text` → `TextResponse`, `type: tool_use` → `ToolCallResponse`), `usage.input_tokens`/`usage.output_tokens` → `LlmUsage`, `stop_reason` → finish reason
- Required request headers: `x-api-key: {apiKey}`, `anthropic-version: 2023-06-01`, `Content-Type: application/json`
- Retry with exponential backoff on HTTP 429/5xx; `Retry-After` header support on 429
- Error handling: non-retryable 4xx errors throw descriptive runtime exceptions
- `ClaudeProviderFactory` implementing `LlmProviderFactory`, registered under key `"claude"`
- Unit tests using `com.sun.net.httpserver.HttpServer` (same pattern as `llm-provider-openai` tests)
- Reuse `JsonWriter` and `JsonReader` utilities from `llm-provider-openai`

**Out of scope:**
- Streaming responses (M19)
- Token counting / context window management (M19)
- OpenAI provider (separate active change `llm-provider-openai`)
- Interface changes to `LlmClient`, `LlmProvider`, registry, or any existing type
- Non-Messages-API Claude endpoints (legacy Text Completions)

## Unchanged Behavior

- Existing `LlmClient`, `LlmProvider`, `LlmProviderRegistry`, `LlmRequest`, `LlmResponse`, `LlmConfig`, `LlmUsage`, `ToolSchema`, `SkillRoute`, `LlmProviderFactory`, and `DefaultLlmProviderRegistry` interfaces and implementations MUST NOT change
- `DefaultLlmProviderRegistry.fromConfig()` integration point expects factories keyed by type name — the `"claude"` key is provided by this change without modifying the registry
- All existing tests MUST continue to pass

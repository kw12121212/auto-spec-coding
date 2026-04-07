# Design: llm-provider-claude

## Approach

Three concrete classes in the `org.specdriven.agent.agent` package, mirroring the OpenAI provider structure:

1. **`ClaudeProvider`** implements `LlmProvider` — holds `LlmConfig`, creates `ClaudeClient` instances. `close()` is a no-op (stateless clients). Shares the same structural pattern as `OpenAiProvider`.

2. **`ClaudeClient`** implements `LlmClient` — the core HTTP logic for the Claude Messages API:
   - Uses `java.net.http.HttpClient` (JDK built-in, same as OpenAI client)
   - `chat(LlmRequest)` builds a Claude Messages API JSON body, POSTs to `{baseUrl}/messages`, parses the JSON response
   - `chat(List<Message>)` delegates to `chat(LlmRequest.of(messages))`
   - Retry: up to `maxRetries` attempts with exponential backoff (1s, 2s, 4s...) on HTTP 429/5xx; reads `Retry-After` header on 429
   - Non-retryable 4xx (400, 401, 403, 404) throw descriptive exceptions immediately

3. **`ClaudeProviderFactory`** implements `LlmProviderFactory` — trivial wrapper around `ClaudeProvider::new`. Registered under key `"claude"` for `DefaultLlmProviderRegistry.fromConfig()`.

**JSON handling:** Reuse `JsonWriter` and `JsonReader` utilities from `llm-provider-openai`. No external JSON library.

**Request serialization map (Claude Messages API format):**
- `LlmRequest.systemPrompt()` → top-level `"system"` string field (NOT a message in the array)
- `UserMessage` → `{"role": "user", "content": "<text>"}`
- `AssistantMessage` → `{"role": "assistant", "content": "<text>"}`
- `ToolMessage` → `{"role": "user", "content": [{"type": "tool_result", "tool_use_id": "<toolName>", "content": "<content>"}]}`
- Tool calls in assistant turn → `{"role": "assistant", "content": [{"type": "tool_use", "id": "...", "name": "...", "input": {...}}]}`
- `ToolSchema` → `{"name": "...", "description": "...", "input_schema": {...}}` (no `type: function` wrapper)
- `temperature`, `max_tokens` → top-level fields; `max_tokens` is required by the Claude API (default to 4096 if not set)

**Response parsing map (Claude Messages API format):**
- `content[*].type == "text"` → `TextResponse(content[0].text, usage, stopReason)`
- `content[*].type == "tool_use"` → `ToolCallResponse` with `ToolCall(content[i].name, content[i].input as map)`
- `usage.input_tokens` → `LlmUsage.promptTokens()`
- `usage.output_tokens` → `LlmUsage.completionTokens()`
- `input_tokens + output_tokens` → `LlmUsage.totalTokens()`
- `stop_reason` → finish reason string

**Required HTTP headers:**
- `x-api-key: {apiKey}`
- `anthropic-version: 2023-06-01`
- `Content-Type: application/json`

## Key Decisions

1. **Reuse `JsonWriter`/`JsonReader` from `llm-provider-openai`** — these utilities handle flat JSON structures. The Claude API format is similarly flat, just with a different schema. Reusing avoids code duplication; if the utilities are package-private they may need visibility adjustment.

2. **`ToolMessage` maps to `user` role with `tool_result` content block** — The Claude API does not have a dedicated `tool` role. Tool results are delivered as `user` messages with structured content. This is the standard Claude convention and must be maintained exactly.

3. **`max_tokens` is required by Claude API** — Unlike OpenAI where it's optional, Claude's Messages API returns a 400 error if `max_tokens` is omitted. Default to 4096 if `LlmRequest.maxTokens()` is not set.

4. **`anthropic-version` header is required** — The Claude API requires this header on every request. Use `2023-06-01` as the stable version string (current as of the knowledge cutoff). This is a fixed constant, not configurable.

5. **Same retry policy as OpenAI provider** — 429/5xx retried with exponential backoff, 4xx non-retried. Keeps behavior consistent across providers so the registry layer can treat them uniformly.

6. **`baseUrl` defaults to `https://api.anthropic.com/v1`** — Endpoint is `{baseUrl}/messages`. The `/v1` is part of the base URL, matching the OpenAI convention.

## Alternatives Considered

1. **Single `ClaudeClient` class without `ClaudeProvider`** — Would break the `LlmProvider`/`LlmClient` interface separation already established. The two-class design is required by the interface contract.

2. **Abstract base class shared by OpenAI and Claude clients** — Both clients share retry logic and JSON utilities, but their wire formats diverge significantly (different auth headers, different request/response shapes). An abstraction would be forced and fragile. Keep them independent; share utilities only.

3. **Configurable `anthropic-version`** — Adding it to `LlmConfig` would complicate configuration with minimal benefit — the API version is stable and rarely changes. Hard-coding `2023-06-01` is simpler and correct for the current API.

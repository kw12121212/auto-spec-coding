# Design: llm-provider-openai

## Approach

Two concrete classes + one factory, all in the `org.specdriven.agent.agent` package:

1. **`OpenAiProvider`** implements `LlmProvider` — holds `LlmConfig`, creates `OpenAiClient` instances. Each client shares the provider's config. `close()` is a no-op (stateless clients).

2. **`OpenAiClient`** implements `LlmClient` — the core HTTP logic:
   - Uses `java.net.http.HttpClient` (JDK 11+ built-in) for HTTP calls. Lealone's `lealone-net` provides TCP-level networking only; it does not ship an HTTP client. `java.net.http.HttpClient` supports async and works well with VirtualThreads, adding zero external dependencies.
   - `chat(LlmRequest)` builds an OpenAI Chat Completions JSON body, POSTs to `{baseUrl}/chat/completions`, parses the JSON response.
   - `chat(List<Message>)` delegates to `chat(LlmRequest.of(messages))`.
   - Retry: up to `maxRetries` attempts with exponential backoff (1s, 2s, 4s...) on HTTP 429/5xx. Reads `Retry-After` header on 429.

3. **`OpenAiProviderFactory`** implements `LlmProviderFactory` — trivial lambda wrapping `OpenAiProvider::new`. Registered under key `"openai"` for `DefaultLlmProviderRegistry.fromConfig()`.

**JSON handling:** Use a lightweight manual StringBuilder approach or `java.util.LinkedHashMap` → JSON string conversion. No external JSON library (Jackson, Gson) to keep the zero-external-dependency constraint. The OpenAI request/response format is flat enough for manual serialization.

**Request serialization map:**
- `SystemMessage` → `{"role": "system", "content": "..."}`
- `UserMessage` → `{"role": "user", "content": "..."}`
- `AssistantMessage` → `{"role": "assistant", "content": "..."}`
- `ToolMessage` → `{"role": "tool", "content": "...", "tool_call_id": "..."}` (tool_call_id mapped from toolName)
- `ToolSchema` → `{"type": "function", "function": {"name": "...", "description": "...", "parameters": {...}}}`
- System prompt → injected as first message if present
- `temperature`, `max_tokens` → top-level JSON fields

**Response parsing map:**
- `choices[0].message.content` → `TextResponse`
- `choices[0].message.tool_calls` → `ToolCallResponse` with `ToolCall` list
- `usage` → `LlmUsage(promptTokens, completionTokens, totalTokens)`
- `finish_reason` → passed through as string

## Key Decisions

1. **Use `java.net.http.HttpClient` instead of Lealone net** — Lealone's `lealone-net` provides TCP-level networking only (`NetClient`, `TcpClientConnection`); it does not include an HTTP client. `java.net.http.HttpClient` is built into the JDK, supports async, and integrates well with VirtualThreads. Zero new dependencies.

2. **Manual JSON serialization** — The OpenAI Chat Completions format is relatively flat and predictable. Adding Jackson or Gson for this single use case violates the project's minimal-dependency principle. A simple `JsonWriter` utility class handles serialization; a `JsonParser` utility handles response parsing.

3. **Stateless client instances** — `OpenAiClient` holds no mutable state; `HttpClient` is thread-safe and shared. Multiple calls to `OpenAiProvider.createClient()` can return the same instance or new instances — the interface contract doesn't require identity semantics.

4. **Retry on transient errors only** — 429 (rate limit), 500, 502, 503, 504 (server errors) are retried. 400 (bad request), 401 (auth), 403 (forbidden), 404 (not found) are not retried — these indicate configuration or request issues.

5. **`baseUrl` includes `/v1`** — The config's `baseUrl` defaults to `https://api.openai.com/v1` and the client appends `/chat/completions`. Compatible providers just change the base URL.

## Alternatives Considered

1. **Build HTTP client on Lealone TCP networking** — Would require implementing HTTP/1.1 protocol (request framing, chunked encoding, TLS) on top of Lealone's `NetClient`. Far too much work for no benefit when the JDK ships a solid HTTP client.

2. **Add Jackson/Gson dependency** — Would simplify JSON handling but adds a significant external dependency for a narrow use case. The manual approach is sufficient for the flat OpenAI JSON format. Can be revisited if JSON complexity grows (e.g., if response parsing becomes fragile).

3. **Single provider class (no separate client)** — Would work but breaks the `LlmProvider`/`LlmClient` separation already established in the interface layer. The two-class design allows providers to manage shared resources (connection pools, auth tokens) independently of per-call state.

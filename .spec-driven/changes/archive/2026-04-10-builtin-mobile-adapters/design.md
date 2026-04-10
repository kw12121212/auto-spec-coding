# Design: builtin-mobile-adapters

## Approach

Each adapter follows the same two-component pattern established by `MobileChannelProvider`:

1. **Delivery channel** — receives a `Question`, extracts the plain-text payload (`question`, `impact`, `recommendation`), and sends it to the remote channel via HTTP. Uses Lealone's async HTTP client (`lealone-net`) for outbound requests. Credentials (bot token, webhook URL, etc.) are resolved from `SecretVault` at construction time using the `vaultKey` from `MobileChannelConfig`.

2. **Reply collector** — exposes an HTTP callback endpoint that the channel calls when a human replies. Validates the incoming payload against the expected question, constructs an `Answer` with `source == HUMAN_MOBILE`, and calls `QuestionReplyCollector.collect()`. For v1, callback URLs are assembled from config overrides (`callbackBaseUrl` + adapter-specific path).

3. **Provider** — implements `MobileChannelProvider.create(config)` to wire vault, HTTP client, and callback URL into the channel/collector pair. Registered under `"telegram"` and `"discord"` names in the `MobileChannelRegistry`.

4. **Auto-registration** — a static utility `BuiltinMobileAdapters` registers both providers into a given `MobileChannelRegistry` so `SdkBuilder` can call it during build.

### HTTP client usage

Both adapters use `lealone-net`'s `HttpClient` for outbound POST requests. No additional HTTP dependency is needed. Request bodies are JSON serialized via Jackson (`ObjectMapper`), which is already a project dependency.

### Message format (plain text only for v1)

Question payloads are formatted as simple plain-text messages:
```
[Question] {question}
[Impact] {impact}
[Recommendation] {recommendation}
[Session] {sessionId}
[Question ID] {questionId}
```

A `RichMessageFormatter` interface is defined as an extension point but only a `PlainTextFormatter` implementation is provided in this change.

### Credential resolution

Each adapter resolves its credentials from `SecretVault` using the `vaultKey` from config:
- **Telegram**: vault key maps to the bot token (`{vaultKey}.token`)
- **Discord**: vault key maps to the webhook URL (`{vaultKey}.webhookUrl`)

Channel-specific overrides from `MobileChannelConfig.overrides()` provide additional parameters like `chatId` (Telegram) or `callbackBaseUrl` (both).

## Key Decisions

1. **Plain text only for v1** — Rich formatting (MarkdownV2, embeds) varies wildly between channels and would complicate the SPI. A `RichMessageFormatter` extension point is included but only plain text is implemented. Confirmed by user.

2. **Lealone HTTP client, no new dependencies** — The project already depends on `lealone-net` for HTTP. Adding OkHttp or Apache HttpClient would break the minimal-dependency principle. Lealone's client is sufficient for outbound POST and webhook callback handling.

3. **Callback URL from config, not auto-discovered** — The reply collector needs a publicly reachable callback URL. Rather than trying to auto-detect the server's public URL, adapters require `callbackBaseUrl` in config overrides. This keeps the adapter stateless and testable.

4. **SecretVault for credentials** — All channel secrets are stored in and resolved from the M18 vault. Adapters never handle raw credentials in config — only vault key references.

5. **Single `MobileAdapterException` type** — Rather than per-channel exception hierarchies, a single `MobileAdapterException` with a `channelType` field covers all adapter failures (connection, auth, rate-limit, parse errors). Keeps the error surface simple.

6. **Static auto-registration** — `BuiltinMobileAdapters.registerAll(registry)` is called by `SdkBuilder` rather than using SPI/service-loader. The adapter count is small and known at compile time.

## Alternatives Considered

- **Per-channel exception types** — Would give callers more granular error handling, but adds complexity for only two adapters. Rejected in favor of single exception with `channelType` discriminator.

- **Embed HTTP callback server in adapter** — Some adapter designs embed a lightweight HTTP server for webhook callbacks. Rejected because Lealone already provides an HTTP server (M14). Callback endpoints should be added to the existing HTTP servlet layer, not spawn additional servers.

- **Service-loader auto-discovery of providers** — Would allow third-party adapters to be discovered automatically. Rejected for v1 — the two built-in adapters are known at compile time. Can be added later when third-party adapters are supported.

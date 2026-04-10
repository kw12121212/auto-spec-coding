# Design: mobile-reply-callbacks

## Approach

Add a `ReplyCallbackRouter` as the central routing component between the HTTP layer and the reply collectors. When channels are assembled via `MobileChannelRegistry.assembleAll()`, each handle's collector is registered with the router. The `HttpApiServlet` receives the router reference and dispatches `POST /api/v1/callbacks/{channelType}` requests through it.

### Flow

```
Telegram/Discord webhook → POST /api/v1/callbacks/{channelType}
  → HttpApiServlet reads body + signature headers
  → ReplyCallbackRouter.dispatch(channelType, payload, headers)
    → Telegram: verify X-Telegram-Bot-Api-Secret-Token header
    → Discord: forward signature to DiscordReplyCollector.processCallback(payload, signature)
  → Collector parses reply, creates Answer, submits to QuestionRuntime
  → DefaultOrchestrator.pollAnswer() picks up the answer, resumes agent
```

### Signature verification

- **Telegram**: Verify the `X-Telegram-Bot-Api-Secret-Token` header against the configured secret (stored in vault at `{vaultKey}.webhookSecret`). The verification happens at the router level, not inside `TelegramReplyCollector`, because the collector's spec interface takes only `jsonPayload`.
- **Discord**: Signature verification is already inside `DiscordReplyCollector.processCallback(payload, signatureHeader)`. The router passes the `X-Signature-256` header through.

### Auth bypass

Callback endpoints receive requests from external services (Telegram/Discord API servers), not from our users. They MUST bypass the existing `AuthFilter` and `RateLimitFilter`. The router handles security via channel-specific signature verification.

## Key Decisions

1. **Callback routes under existing `/api/v1/callbacks/*`** — keeps all HTTP surface in one servlet; no new servlet class needed.
2. **Router-level Telegram verification** — avoids modifying the existing `TelegramReplyCollector` interface and spec.
3. **Discord signature passthrough** — the `DiscordReplyCollector` already verifies HMAC-SHA256; no duplication.
4. **Router receives collector references at assembly time** — keeps lifecycle management in the existing `BuiltinMobileAdapters.registerAll()` flow.

## Alternatives Considered

1. **Separate `CallbackServlet`** — rejected because it adds a new servlet registration and duplicates the HTTP infrastructure (JSON codec, error handling). Adding a route group to the existing servlet is simpler.
2. **Signature verification inside `TelegramReplyCollector`** — would require changing the existing `processCallback(String)` interface to also accept headers, breaking the current spec contract. Router-level verification keeps the collector unchanged.
3. **Callback URL as a config override** — instead of hardcoding `/api/v1/callbacks/{channelType}`, the callback URL could be fully configurable. Rejected for first implementation: the standard URL pattern is sufficient, and webhook registration is out of scope for this change.

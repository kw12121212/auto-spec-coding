# mobile-reply-callbacks

## What

Wire HTTP callback endpoints into the existing HTTP API layer so that mobile channel webhooks (Telegram, Discord) can deliver human replies back into the agent execution flow. The endpoints receive incoming webhook payloads, verify channel-specific signatures, route to the correct `QuestionReplyCollector`, and let the existing `QuestionRuntime` + `DefaultOrchestrator` polling mechanism resume the paused agent session.

## Why

The previous m23 changes built the full outbound pipeline: delivery channels send templated questions to Telegram/Discord, and reply collectors parse and validate incoming replies. However, the HTTP layer that actually receives the webhook callbacks from Telegram/Discord and routes them to the collectors does not exist yet. Without it, human replies arrive at the external channel but never reach the agent — the two-way communication loop is broken at the inbound side.

## Scope

- `ReplyCallbackRouter` — registry that maps channel type names to assembled `QuestionReplyCollector` instances
- HTTP callback routes in `HttpApiServlet` — `POST /api/v1/callbacks/{channelType}` dispatching to the router
- Telegram webhook `secret_token` header verification at the HTTP endpoint level
- Discord HMAC-SHA256 signature passthrough (already implemented in `DiscordReplyCollector`, wired through the endpoint)
- Integration wiring: register assembled collectors with the router during channel setup

## Unchanged Behavior

- Existing `TelegramDeliveryChannel` / `DiscordDeliveryChannel` send behavior
- Existing `TelegramReplyCollector.processCallback()` / `DiscordReplyCollector.processCallback()` parsing logic
- `QuestionRuntime` answer submission and lifecycle management
- `DefaultOrchestrator.waitForAnswer()` polling and agent resumption
- Message templates, field policies, and masking strategies
- Mobile channel config registry and provider assembly

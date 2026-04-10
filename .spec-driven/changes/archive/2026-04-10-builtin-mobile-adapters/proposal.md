# builtin-mobile-adapters

## What

Provide two built-in mobile interaction adapters — Telegram and Discord — that implement the `MobileChannelProvider` SPI defined in `mobile-channel-config-registry`. Each adapter wires a `QuestionDeliveryChannel` (sends question notifications to the channel) and a `QuestionReplyCollector` (receives human replies via webhook callback). Both adapters use the M18 SecretVault for credential management and plain-text message formatting in the first release.

## Why

M22's question-resolution system and M23's config registry are in place, but no concrete adapters exist. Without built-in adapters, integrators must implement channel-specific HTTP clients, webhook listeners, and signature verification themselves — defeating the "configure and go" goal of M23. Telegram and Discord represent two distinct messaging patterns (Bot API vs. webhook-first) that cover the most common mobile notification use cases.

## Scope

- `TelegramDeliveryChannel` — sends question payloads to a Telegram chat via Bot API `sendMessage`
- `TelegramReplyCollector` — receives human replies via a Telegram webhook callback, verifies the bot token
- `TelegramChannelProvider` — `MobileChannelProvider` implementation that assembles the Telegram pair
- `DiscordDeliveryChannel` — sends question payloads to a Discord channel via webhook URL
- `DiscordReplyCollector` — receives human replies via a Discord interaction callback, verifies the webhook signature
- `DiscordChannelProvider` — `MobileChannelProvider` implementation that assembles the Discord pair
- `MobileAdapterException` — unified exception type for adapter-specific failures
- Unit tests for both adapters using mocked HTTP endpoints and vault
- Auto-registration of both providers via `MobileChannelRegistry` on SDK build

Out of scope:
- Rich message formatting (MarkdownV2, Discord embeds) — extension point only
- Additional channels beyond Telegram and Discord
- Message templating / field sanitization (belongs to `question-message-templating`)
- Retry / delivery observability (belongs to `mobile-delivery-observability`)
- Signature verification beyond basic bot-token / webhook-secret matching

## Unchanged Behavior

- `MobileChannelRegistry` API and semantics unchanged
- `MobileChannelConfig`, `MobileChannelHandle`, `MobileChannelProvider` types unchanged
- `LoggingDeliveryChannel` remains the default when no mobile channels are configured
- `QuestionDeliveryService` behavior unchanged — adapters plug into the existing channel/collector interfaces
- `SdkBuilder` channel wiring logic unchanged — adapters are simply new provider registrations

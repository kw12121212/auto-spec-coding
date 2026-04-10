# question-message-templating

## What

Introduce a question message templating layer that maps `Question` payloads into channel-specific message bodies with field trimming, default copy, and security masking. Provide concrete templates for the built-in Telegram and Discord channels, replacing the current hardcoded `PlainTextFormatter` with configurable, per-channel templates.

## Why

M23's completed changes (`mobile-channel-config-registry`, `builtin-mobile-adapters`) established the channel provider registry and adapter infrastructure. Both `TelegramDeliveryChannel` and `DiscordDeliveryChannel` currently use `PlainTextFormatter.INSTANCE` — a hardcoded, single-format formatter that dumps all fields uniformly regardless of channel capabilities. This prevents:

- Telegram from using MarkdownV2 or HTML formatting for readable messages
- Discord from leveraging rich embeds for structured question display
- Channel operators from customizing copy or trimming sensitive fields (e.g., hiding `sessionId` from mobile end-users)
- Consistent application of security masking for PII or credential fields

Templating is also a prerequisite for `mobile-reply-callbacks` (human replies need to correlate back to the original templated message) and `mobile-delivery-observability` (delivery tracking needs to know which template variant was sent).

## Scope

- Define a `QuestionMessageTemplate` type that describes how a `Question` is rendered for a specific channel
- Define a `TemplateFieldPolicy` controlling which fields are included, trimmed, or masked per template
- Define a `MaskingStrategy` interface for security masking of sensitive field values
- Provide `TelegramMessageTemplate` and `DiscordMessageTemplate` default implementations
- Provide `DefaultMaskingStrategy` implementing common masking patterns (email, API key, session ID)
- Extend `RichMessageFormatter` or provide a bridge so existing channels can use templates
- Unit tests covering field inclusion, masking, channel-specific formatting, and default copy

## Out of Scope

- Custom user-editable template files (YAML/JSON template resources) — first version uses code-defined templates
- Template versioning or migration
- Localization / i18n of template copy
- Observability of delivery results (covered by `mobile-delivery-observability`)
- Reply callback correlation logic (covered by `mobile-reply-callbacks`)

## Unchanged Behavior

- Existing `PlainTextFormatter` behavior MUST remain unchanged as the universal fallback
- `TelegramDeliveryChannel` and `DiscordDeliveryChannel` public constructors MUST remain backward-compatible
- `RichMessageFormatter` interface signature MUST NOT change
- `Question.toPayload()` output MUST NOT change
- `LoggingDeliveryChannel` behavior MUST NOT change

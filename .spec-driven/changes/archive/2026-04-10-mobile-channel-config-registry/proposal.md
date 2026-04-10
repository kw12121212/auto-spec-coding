# mobile-channel-config-registry

## What

Define the configuration model, provider SPI, and registry for mobile interaction channels. This is the foundation change for M23 — it provides the typed config structure, the factory interface that concrete adapters will implement, and the registry that maps channel names to providers. It also adds SdkBuilder integration so integrators can register channels via config or code.

## Why

M22 defined the `QuestionDeliveryChannel` / `QuestionReplyCollector` extension points but only ships a `LoggingDeliveryChannel` default. To make the question-resolution system usable from mobile devices, integrators need a structured way to configure and assemble real delivery channels. Without this registry layer, every channel implementation would be wired ad-hoc with no consistent config model or SDK integration.

## Scope

- `MobileChannelConfig` — immutable record describing a single channel's type, credential references, and optional overrides
- `MobileChannelProvider` — factory SPI: given a config, produce a `QuestionDeliveryChannel` + `QuestionReplyCollector` pair
- `MobileChannelRegistry` — name-to-provider lookup, programmatic registration, and config-driven assembly
- `SdkBuilder` methods for registering providers and channel configs
- Unit tests for config validation, registry lookup, and assembly

Out of scope:
- Concrete mobile adapters (Telegram, Discord, push — belongs to `builtin-mobile-adapters`)
- Message templating / field sanitization (belongs to `question-message-templating`)
- Human reply callback / signature verification (belongs to `mobile-reply-callbacks`)
- Delivery observability / retry / audit (belongs to `mobile-delivery-observability`)

## Unchanged Behavior

- Existing `LoggingDeliveryChannel` remains the default when no mobile channels are configured
- `QuestionDeliveryService` behavior is unchanged when used without mobile channel config
- `SdkAgent.pendingQuestions()` and `SdkAgent.submitHumanReply()` signatures and semantics are preserved
- `SdkBuilder.deliveryModeOverride()` continues to work as before

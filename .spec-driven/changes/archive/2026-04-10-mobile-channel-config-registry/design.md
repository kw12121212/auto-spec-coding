# Design: mobile-channel-config-registry

## Approach

Add a thin configuration and registry layer between the existing `QuestionDeliveryChannel` / `QuestionReplyCollector` interfaces and the future concrete adapters. The layer has three parts:

1. **MobileChannelConfig** — a record holding channel type name, a vault key reference for credentials, and a `Map<String, String>` of channel-specific overrides. Validation rejects missing type or empty vault key at construction time.

2. **MobileChannelProvider** — a functional interface that takes a `MobileChannelConfig` and returns a `MobileChannelHandle` (record wrapping a `QuestionDeliveryChannel` + `QuestionReplyCollector`). Providers are registered by name string.

3. **MobileChannelRegistry** — holds a `Map<String, MobileChannelProvider>` and a `List<MobileChannelConfig>`. Exposes `registerProvider(String name, MobileChannelProvider)`, `provider(String name)`, and `assembleAll()` which iterates configs, resolves each through its provider, and returns the assembled handles.

SdkBuilder gets two new methods:
- `registerChannelProvider(String name, MobileChannelProvider)` — programmatic provider registration
- `channelConfigs(List<MobileChannelConfig>)` — config-driven channel list

When `build()` is called with channel configs, the registry assembles handles and wires them into `QuestionDeliveryService` instead of the default `LoggingDeliveryChannel`.

## Key Decisions

- **Vault keys, not raw credentials** — `MobileChannelConfig` stores a vault key reference string, not inline secrets. Actual secret resolution happens at assembly time via the existing vault infrastructure. This keeps config serializable and safe to log.
- **Provider as SPI, not per-channel subclass** — each provider is a named factory function, not a class hierarchy. This keeps the barrier to adding new channels low: implement one interface, register by name.
- **Handle record pairs channel + collector** — mobile channels often need a matched sender/receiver pair (e.g., a webhook sender and a callback receiver). Returning them as a single `MobileChannelHandle` avoids mismatched wiring.
- **Assembly at build time, not lazy** — all configured channels are assembled during `SdkBuilder.build()`, so config errors surface immediately rather than at first question delivery.

## Alternatives Considered

- **Embed channel config in QuestionDeliveryService constructor** — rejected: couples delivery service to channel details, makes it harder to add channels incrementally.
- **Use a generic ServiceLoader plugin system** — rejected: YAGNI. A simple named registry is sufficient for the planned 2–3 channel types. Can add ServiceLoader discovery later if the count grows.
- **Config-driven only, no programmatic registration** — rejected: tests and advanced integrators need programmatic control. Supporting both is trivial.

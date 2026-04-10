# sdk-public-api.md delta

## ADDED Requirements

### Requirement: SdkBuilder channel provider registration

The `SdkBuilder` MUST support registering mobile channel providers for use by all agents.

#### Scenario: Register channel provider
- GIVEN a builder
- WHEN `.registerChannelProvider("telegram", provider)` is called then `.build()` is invoked
- THEN the provider MUST be available in the internal registry

#### Scenario: Channel providers from config
- GIVEN a builder with `.config(Path)` where the YAML contains a `mobile-channels` section
- WHEN `.build()` is invoked
- THEN providers matching the configured channel types MUST be resolved from the registry

### Requirement: SdkBuilder channel configs

The `SdkBuilder` MUST support setting mobile channel configurations.

#### Scenario: Set channel configs
- GIVEN a builder
- WHEN `.channelConfigs(configs)` is called with a list of `MobileChannelConfig` then `.build()` is invoked
- THEN the configs MUST be used to assemble channel handles at build time

### Requirement: SdkBuilder wires mobile channels into delivery service

When mobile channel configs and providers are registered, the `SdkBuilder` MUST wire the assembled channels into the `QuestionDeliveryService`.

#### Scenario: Mobile channel replaces default channel
- GIVEN a builder with a registered provider and a matching channel config
- WHEN `.build()` is invoked
- THEN the `QuestionDeliveryService` MUST use the assembled mobile channel instead of `LoggingDeliveryChannel`
- AND the `QuestionDeliveryService` MUST use the assembled reply collector instead of `InMemoryReplyCollector`

#### Scenario: No channels preserves defaults
- GIVEN a builder without channel configs
- WHEN `.build()` is invoked
- THEN the `QuestionDeliveryService` MUST use `LoggingDeliveryChannel` and `InMemoryReplyCollector` as before

# question-resolution.md delta

## ADDED Requirements

### Requirement: MobileChannelConfig

The system MUST define a `MobileChannelConfig` record in `org.specdriven.agent.question` for configuring a single mobile interaction channel.

#### Scenario: Config contains required fields
- GIVEN a `MobileChannelConfig` instance
- THEN it MUST expose `channelType` (String)
- AND it MUST expose `vaultKey` (String)
- AND it MUST expose `overrides` (Map<String, String>)

#### Scenario: Config rejects missing channel type
- GIVEN a `MobileChannelConfig` construction with null or blank `channelType`
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: Config rejects missing vault key
- GIVEN a `MobileChannelConfig` construction with null or blank `vaultKey`
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: Overrides default to empty map
- GIVEN a `MobileChannelConfig` construction without explicit overrides
- THEN `overrides` MUST return an empty map

### Requirement: MobileChannelHandle

The system MUST define a `MobileChannelHandle` record in `org.specdriven.agent.question` wrapping a matched delivery channel and reply collector pair.

#### Scenario: Handle exposes channel and collector
- GIVEN a `MobileChannelHandle` instance
- THEN it MUST expose a `QuestionDeliveryChannel channel()`
- AND it MUST expose a `QuestionReplyCollector collector()`

### Requirement: MobileChannelProvider

The system MUST define a `MobileChannelProvider` functional interface in `org.specdriven.agent.question` for creating channel handles from config.

#### Scenario: Provider creates handle from config
- GIVEN a registered `MobileChannelProvider` and a valid `MobileChannelConfig`
- WHEN `create(config)` is called
- THEN it MUST return a non-null `MobileChannelHandle`

### Requirement: MobileChannelRegistry

The system MUST define a `MobileChannelRegistry` in `org.specdriven.agent.question` for managing named channel providers and assembling channel handles from config.

#### Scenario: Register provider by name
- GIVEN a `MobileChannelRegistry` instance
- WHEN `registerProvider("telegram", provider)` is called
- THEN `provider("telegram")` MUST return that provider

#### Scenario: Reject duplicate provider name
- GIVEN a registry with a provider registered under "telegram"
- WHEN `registerProvider("telegram", otherProvider)` is called
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: List registered providers
- GIVEN a registry with two providers registered
- WHEN `registeredProviders()` is called
- THEN it MUST return a set containing both provider names

#### Scenario: Assemble all configured channels
- GIVEN a registry with providers registered for "telegram" and "discord"
- AND a list of two `MobileChannelConfig` instances with matching types
- WHEN `assembleAll(configs)` is called
- THEN it MUST return a list of `MobileChannelHandle` in config order
- AND each handle MUST be produced by the matching provider

#### Scenario: Reject unknown channel type on assembly
- GIVEN a registry with no provider for "slack"
- AND a config with `channelType` "slack"
- WHEN `assembleAll(configs)` is called
- THEN it MUST throw `IllegalArgumentException` identifying the unknown type

#### Scenario: Assemble empty config list
- GIVEN a registry with registered providers
- AND an empty config list
- WHEN `assembleAll(configs)` is called
- THEN it MUST return an empty list

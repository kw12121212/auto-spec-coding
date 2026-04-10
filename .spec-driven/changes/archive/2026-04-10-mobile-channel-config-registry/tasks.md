# Tasks: mobile-channel-config-registry

## Implementation

- [x] Create `MobileChannelConfig` record in `org.specdriven.agent.question` with fields `channelType` (String), `vaultKey` (String), `overrides` (Map<String,String>), and constructor validation
- [x] Create `MobileChannelHandle` record in `org.specdriven.agent.question` wrapping `QuestionDeliveryChannel` + `QuestionReplyCollector`
- [x] Create `MobileChannelProvider` functional interface in `org.specdriven.agent.question` with method `MobileChannelHandle create(MobileChannelConfig config)`
- [x] Create `MobileChannelRegistry` in `org.specdriven.agent.question` with `registerProvider`, `provider`, `registeredProviders`, and `assembleAll` methods
- [x] Add `registerChannelProvider(String, MobileChannelProvider)` method to `SdkBuilder`
- [x] Add `channelConfigs(List<MobileChannelConfig>)` method to `SdkBuilder`
- [x] Wire registry assembly into `SdkBuilder.build()` — when channel configs are present, assemble handles and use first handle's channel/collector in `QuestionDeliveryService` instead of defaults
- [x] Update `question-resolution.md` delta spec with new requirements
- [x] Update `sdk-public-api.md` delta spec with new builder methods

## Testing

- [x] Run `mvn compile` to verify compilation
- [x] Unit test: create `MobileChannelConfigTest` — validate construction, reject missing type, reject empty vault key
- [x] Unit test: create `MobileChannelRegistryTest` — register provider, lookup by name, assemble all configs, reject duplicate names, reject unknown type on assembly
- [x] Unit test: create `MobileChannelHandleTest` — verify record fields
- [x] Run `mvn test` to verify all tests pass

## Verification

- [x] Verify `MobileChannelConfig` validation matches spec scenarios
- [x] Verify `MobileChannelRegistry.assembleAll()` returns handles in config order
- [x] Verify `SdkBuilder` wires assembled channels into `QuestionDeliveryService` when present
- [x] Verify default `LoggingDeliveryChannel` is still used when no channels configured

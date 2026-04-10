# Tasks: question-message-templating

## Implementation

- [x] Define `TemplateFieldPolicy` enum with `INCLUDE`, `TRIM`, `MASK` values in `org.specdriven.agent.question`
- [x] Define `MaskingStrategy` functional interface with `mask(String fieldName, String value)` in `org.specdriven.agent.question`
- [x] Implement `DefaultMaskingStrategy` with email, API key/token, and generic masking patterns
- [x] Define `QuestionMessageTemplate` implementing `RichMessageFormatter` with field policy map, masking strategy, default copy, and `channelType` accessor
- [x] Implement `TelegramMessageTemplate` extending `QuestionMessageTemplate` with MarkdownV2 bold formatting and channelType "telegram"
- [x] Implement `DiscordMessageTemplate` extending `QuestionMessageTemplate` with markdown bold formatting and channelType "discord"
- [x] Update `TelegramDeliveryChannel` constructor to accept a `RichMessageFormatter` parameter (overload, keep existing constructor backward-compatible)
- [x] Update `DiscordDeliveryChannel` constructor to accept a `RichMessageFormatter` parameter (overload, keep existing constructor backward-compatible)
- [x] Update `TelegramChannelProvider.create()` to use `TelegramMessageTemplate` as the default formatter
- [x] Update `DiscordChannelProvider.create()` to use `DiscordMessageTemplate` as the default formatter

## Testing

- [x] Lint/validation: `mvn compile -pl . -q`
- [x] Run `mvn test -pl . -Dtest="org.specdriven.agent.question.*TemplateTest" -q` for template unit tests
- [x] Unit test: `TemplateFieldPolicy` enum values and behavior
- [x] Unit test: `DefaultMaskingStrategy` masking patterns (email, API key, generic, null)
- [x] Unit test: `QuestionMessageTemplate` INCLUDE/TRIM/MASK policies, default copy for empty fields, channelType
- [x] Unit test: `TelegramMessageTemplate` MarkdownV2 formatting output, channelType "telegram"
- [x] Unit test: `DiscordMessageTemplate` markdown formatting output, channelType "discord"
- [x] Unit test: `TelegramDeliveryChannel` with template formatter sends templated text
- [x] Unit test: `DiscordDeliveryChannel` with template formatter sends templated content

## Verification

- [x] Verify `PlainTextFormatter.INSTANCE` still works unchanged for any channel using it
- [x] Verify existing `TelegramDeliveryChannel` and `DiscordDeliveryChannel` tests still pass
- [x] Verify `LoggingDeliveryChannel` is unaffected
- [x] Verify `RichMessageFormatter` interface signature is unchanged
- [x] Run full test suite: `mvn test -pl . -q`

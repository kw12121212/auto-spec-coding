# Tasks: builtin-mobile-adapters

## Implementation

- [x] Create `MobileAdapterException` in `org.specdriven.agent.question` with `channelType` field, message, and cause constructors
- [x] Create `RichMessageFormatter` interface and `PlainTextFormatter` implementation that formats a `Question` into a plain-text message containing question, impact, recommendation, sessionId, and questionId
- [x] Create `TelegramDeliveryChannel` implementing `QuestionDeliveryChannel` — resolves bot token from vault, POSTs plain-text question to Telegram Bot API `sendMessage` endpoint, throws `MobileAdapterException` on HTTP errors
- [x] Create `TelegramReplyCollector` implementing `QuestionReplyCollector` — processes incoming Telegram webhook `message` callbacks, constructs `Answer` with `HUMAN_MOBILE` source, validates bot token
- [x] Create `TelegramChannelProvider` implementing `MobileChannelProvider` — validates config has `chatId` override, resolves token from vault, assembles `TelegramDeliveryChannel` + `TelegramReplyCollector` into `MobileChannelHandle`
- [x] Create `DiscordDeliveryChannel` implementing `QuestionDeliveryChannel` — resolves webhook URL from vault, POSTs plain-text question to Discord webhook, throws `MobileAdapterException` on HTTP errors
- [x] Create `DiscordReplyCollector` implementing `QuestionReplyCollector` — processes incoming Discord interaction callbacks, constructs `Answer` with `HUMAN_MOBILE` source, validates webhook signature
- [x] Create `DiscordChannelProvider` implementing `MobileChannelProvider` — validates config has `callbackBaseUrl` override, resolves webhook URL from vault, assembles `DiscordDeliveryChannel` + `DiscordReplyCollector` into `MobileChannelHandle`
- [x] Create `BuiltinMobileAdapters` utility class with `TELEGRAM` / `DISCORD` constants and `registerAll(MobileChannelRegistry)` static method that registers both providers

## Testing

- [x] Lint/validation: `mvn compile -q`
- [x] Unit tests: `mvn test -pl . -Dtest="MobileAdapterExceptionTest,PlainTextFormatterTest,TelegramDeliveryChannelTest,TelegramReplyCollectorTest,TelegramChannelProviderTest,DiscordDeliveryChannelTest,DiscordReplyCollectorTest,DiscordChannelProviderTest,BuiltinMobileAdaptersTest" -DfailIfNoTests=false`
- [x] Write unit tests for `MobileAdapterException` — constructor, channelType, message, cause
- [x] Write unit tests for `PlainTextFormatter` — formats question with all required fields
- [x] Write unit tests for `TelegramDeliveryChannel` — send success, API error, vault resolution
- [x] Write unit tests for `TelegramReplyCollector` — valid reply, wrong token
- [x] Write unit tests for `TelegramChannelProvider` — valid config, missing chatId
- [x] Write unit tests for `DiscordDeliveryChannel` — send success, webhook error, vault resolution
- [x] Write unit tests for `DiscordReplyCollector` — valid reply, invalid signature
- [x] Write unit tests for `DiscordChannelProvider` — valid config, missing callbackBaseUrl
- [x] Write unit tests for `BuiltinMobileAdapters` — registerAll populates registry with both providers

## Verification

- [x] Verify all adapter types implement the correct SPI interfaces from `mobile-channel-config-registry`
- [x] Verify `BuiltinMobileAdapters.registerAll()` produces providers that `MobileChannelRegistry.assembleAll()` can use with matching `MobileChannelConfig` instances
- [x] Verify both adapters resolve credentials exclusively from `SecretVault` (no hardcoded secrets)
- [x] Verify no new external dependencies added beyond existing `lealone-net` and Jackson

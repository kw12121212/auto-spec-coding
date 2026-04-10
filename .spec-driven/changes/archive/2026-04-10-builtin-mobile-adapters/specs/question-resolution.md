# question-resolution.md delta

## ADDED Requirements

### Requirement: MobileAdapterException

The system MUST define a `MobileAdapterException` in `org.specdriven.agent.question` for adapter-specific failures.

#### Scenario: Exception carries channel type
- GIVEN a `MobileAdapterException` constructed with channel type "telegram" and a message
- THEN `channelType()` MUST return "telegram"
- AND `getMessage()` MUST return the provided message

#### Scenario: Exception wraps cause
- GIVEN a `MobileAdapterException` constructed with a cause
- THEN `getCause()` MUST return the provided cause

### Requirement: RichMessageFormatter

The system MUST define a `RichMessageFormatter` interface in `org.specdriven.agent.question` for extensible message formatting.

#### Scenario: Format question as plain text
- GIVEN a `Question` instance
- WHEN `format(question)` is called on the default `PlainTextFormatter`
- THEN the result MUST be a non-empty string containing the question text, impact, recommendation, sessionId, and questionId

### Requirement: BuiltinMobileAdapters

The system MUST provide `BuiltinMobileAdapters` in `org.specdriven.agent.question` for auto-registering built-in channel providers.

#### Scenario: Register all built-in providers
- GIVEN an empty `MobileChannelRegistry`, a `QuestionRuntime`, and a `SecretVault`
- WHEN `BuiltinMobileAdapters.registerAll(registry, runtime, vault)` is called
- THEN the registry MUST contain providers for "telegram" and "discord"

#### Scenario: Built-in provider names
- GIVEN `BuiltinMobileAdapters` class
- THEN it MUST expose `TELEGRAM` and `DISCORD` constant strings with values "telegram" and "discord"

### Requirement: TelegramDeliveryChannel

The system MUST provide a `TelegramDeliveryChannel` implementing `QuestionDeliveryChannel` that sends question notifications via the Telegram Bot API.

#### Scenario: Send question to Telegram chat
- GIVEN a `TelegramDeliveryChannel` configured with a valid bot token and chat ID
- AND a `Question` with status `WAITING_FOR_ANSWER`
- WHEN `send(question)` is called
- THEN an HTTP POST MUST be sent to `https://api.telegram.org/bot{token}/sendMessage`
- AND the request body MUST include `chat_id` and `text` fields
- AND `text` MUST contain the question text, impact, and recommendation

#### Scenario: Telegram API error throws MobileAdapterException
- GIVEN a `TelegramDeliveryChannel` configured with an invalid bot token
- WHEN `send(question)` is called
- THEN it MUST throw `MobileAdapterException` with channel type "telegram"

#### Scenario: Resolve credentials from vault
- GIVEN a `TelegramDeliveryChannel` constructed with a `SecretVault`
- WHEN the channel sends a question
- THEN the bot token MUST be resolved from the vault using the configured vault key with `.token` suffix

### Requirement: TelegramReplyCollector

The system MUST provide a `TelegramReplyCollector` implementing `QuestionReplyCollector` that receives human replies from Telegram webhook callbacks.

#### Scenario: Collect valid Telegram reply
- GIVEN a `TelegramReplyCollector` with a shared message map containing a mapping from a Telegram message_id to a sessionId
- AND a pending question in `WAITING_FOR_ANSWER` for that session
- AND a valid Telegram Update JSON payload with a `message.reply_to_message.message_id` matching the mapped message
- WHEN `processCallback(jsonPayload)` is called
- THEN an `Answer` MUST be constructed with `source == HUMAN_MOBILE`
- AND `deliveryMode == PUSH_MOBILE_WAIT_HUMAN`
- AND the answer MUST be submitted to the `QuestionRuntime`

#### Scenario: Reject callback without message field
- GIVEN a `TelegramReplyCollector`
- AND a JSON payload with no `message` field
- WHEN `processCallback(jsonPayload)` is called
- THEN it MUST throw `MobileAdapterException` with channel type "telegram"

#### Scenario: Reject callback that is not a reply
- GIVEN a `TelegramReplyCollector`
- AND a JSON payload with a `message` but no `reply_to_message`
- WHEN `processCallback(jsonPayload)` is called
- THEN it MUST throw `MobileAdapterException` with channel type "telegram"

### Requirement: TelegramChannelProvider

The system MUST provide a `TelegramChannelProvider` implementing `MobileChannelProvider`.

#### Scenario: Create handle from config
- GIVEN a `MobileChannelConfig` with `channelType == "telegram"`
- AND valid vault entries for the bot token
- WHEN `create(config)` is called
- THEN it MUST return a `MobileChannelHandle` with a `TelegramDeliveryChannel` and `TelegramReplyCollector`

#### Scenario: Config requires chatId override
- GIVEN a `MobileChannelConfig` with `channelType == "telegram"` but no `chatId` in overrides
- WHEN `create(config)` is called
- THEN it MUST throw `MobileAdapterException` indicating missing chatId

### Requirement: DiscordDeliveryChannel

The system MUST provide a `DiscordDeliveryChannel` implementing `QuestionDeliveryChannel` that sends question notifications via Discord webhook.

#### Scenario: Send question to Discord channel
- GIVEN a `DiscordDeliveryChannel` configured with a valid webhook URL
- AND a `Question` with status `WAITING_FOR_ANSWER`
- WHEN `send(question)` is called
- THEN an HTTP POST MUST be sent to the webhook URL
- AND the request body MUST include a `content` field with the formatted question text

#### Scenario: Discord webhook error throws MobileAdapterException
- GIVEN a `DiscordDeliveryChannel` configured with an invalid webhook URL
- WHEN `send(question)` is called
- THEN it MUST throw `MobileAdapterException` with channel type "discord"

#### Scenario: Resolve webhook URL from vault
- GIVEN a `DiscordDeliveryChannel` constructed with a `SecretVault`
- WHEN the channel sends a question
- THEN the webhook URL MUST be resolved from the vault using the configured vault key with `.webhookUrl` suffix

### Requirement: DiscordReplyCollector

The system MUST provide a `DiscordReplyCollector` implementing `QuestionReplyCollector` that receives human replies from Discord interaction callbacks.

#### Scenario: Collect valid Discord reply
- GIVEN a `DiscordReplyCollector` with a shared message map and a known webhook secret
- AND a pending question in `WAITING_FOR_ANSWER` for a session mapped by message_id
- AND a valid Discord interaction JSON payload with `message_reference.message_id` matching the mapped message
- AND a valid HMAC-SHA256 signature in the header
- WHEN `processCallback(jsonPayload, signatureHeader)` is called
- THEN an `Answer` MUST be constructed with `source == HUMAN_MOBILE`
- AND `deliveryMode == PUSH_MOBILE_WAIT_HUMAN`
- AND the answer MUST be submitted to the `QuestionRuntime`

#### Scenario: Reject callback with invalid signature
- GIVEN a `DiscordReplyCollector` expecting a specific webhook secret
- AND a callback with an invalid HMAC-SHA256 signature
- WHEN `processCallback(jsonPayload, signatureHeader)` is called
- THEN it MUST throw `MobileAdapterException` with channel type "discord"

#### Scenario: Reject callback without message_reference
- GIVEN a `DiscordReplyCollector`
- AND a validly signed JSON payload with no `message_reference`
- WHEN `processCallback(jsonPayload, signatureHeader)` is called
- THEN it MUST throw `MobileAdapterException` with channel type "discord"

### Requirement: DiscordChannelProvider

The system MUST provide a `DiscordChannelProvider` implementing `MobileChannelProvider`.

#### Scenario: Create handle from config
- GIVEN a `MobileChannelConfig` with `channelType == "discord"`
- AND valid vault entries for the webhook URL
- WHEN `create(config)` is called
- THEN it MUST return a `MobileChannelHandle` with a `DiscordDeliveryChannel` and `DiscordReplyCollector`

#### Scenario: Config requires callbackBaseUrl override
- GIVEN a `MobileChannelConfig` with `channelType == "discord"` but no `callbackBaseUrl` in overrides
- WHEN `create(config)` is called
- THEN it MUST throw `MobileAdapterException` indicating missing callbackBaseUrl

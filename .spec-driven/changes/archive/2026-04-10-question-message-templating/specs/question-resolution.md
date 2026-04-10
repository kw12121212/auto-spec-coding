# Delta Spec: question-resolution

## ADDED Requirements

### Requirement: QuestionMessageTemplate

The system MUST define a `QuestionMessageTemplate` in `org.specdriven.agent.question` that implements `RichMessageFormatter` and produces channel-specific formatted messages from a `Question` with field policy and masking applied.

#### Scenario: Template formats question with all included fields
- GIVEN a `QuestionMessageTemplate` with default `INCLUDE` policy for all fields
- AND a `Question` instance
- WHEN `format(question)` is called
- THEN the result MUST contain the question text, impact, recommendation, sessionId, and questionId

#### Scenario: Template applies TRIM policy
- GIVEN a `QuestionMessageTemplate` with `TRIM` policy for the `sessionId` field
- AND a `Question` instance
- WHEN `format(question)` is called
- THEN the result MUST NOT contain the sessionId value

#### Scenario: Template applies MASK policy
- GIVEN a `QuestionMessageTemplate` with `MASK` policy for the `sessionId` field
- AND a `MaskingStrategy` that masks session IDs
- AND a `Question` instance
- WHEN `format(question)` is called
- THEN the result MUST contain a masked version of the sessionId
- AND the raw sessionId MUST NOT appear in the result

#### Scenario: Template substitutes default copy for empty fields
- GIVEN a `QuestionMessageTemplate` with a default text of "N/A"
- AND a `Question` instance with an empty `recommendation` field
- WHEN `format(question)` is called
- THEN the recommendation section MUST display "N/A" instead of an empty string

#### Scenario: Template exposes target channel type
- GIVEN a `QuestionMessageTemplate` instance
- THEN it MUST expose a `channelType` (String) identifying the target channel

### Requirement: TemplateFieldPolicy

The system MUST define a `TemplateFieldPolicy` enum in `org.specdriven.agent.question` controlling how individual question fields are rendered in a template.

#### Scenario: Required policy values
- THEN `TemplateFieldPolicy` MUST include `INCLUDE`
- AND it MUST include `TRIM`
- AND it MUST include `MASK`

#### Scenario: INCLUDE renders the field value as-is
- GIVEN a field with `INCLUDE` policy
- WHEN the template is rendered
- THEN the field value MUST appear in the output unchanged

#### Scenario: TRIM omits the field entirely
- GIVEN a field with `TRIM` policy
- WHEN the template is rendered
- THEN neither the field label nor the field value MUST appear in the output

#### Scenario: MASK applies masking strategy
- GIVEN a field with `MASK` policy and a `MaskingStrategy`
- WHEN the template is rendered
- THEN the field value MUST be transformed by the masking strategy before inclusion

### Requirement: MaskingStrategy

The system MUST define a `MaskingStrategy` functional interface in `org.specdriven.agent.question` for transforming sensitive field values.

#### Scenario: Mask a field value
- GIVEN a `MaskingStrategy` and a field name and value
- WHEN `mask(fieldName, value)` is called
- THEN it MUST return a non-null masked string

#### Scenario: Mask null value returns placeholder
- GIVEN a `MaskingStrategy` and a null value
- WHEN `mask(fieldName, null)` is called
- THEN it MUST return a fixed placeholder string

### Requirement: DefaultMaskingStrategy

The system MUST provide a `DefaultMaskingStrategy` in `org.specdriven.agent.question` implementing common masking patterns.

#### Scenario: Mask email address
- GIVEN a `DefaultMaskingStrategy`
- WHEN an email address "user@example.com" is masked
- THEN the result MUST reveal at most the first two characters before the @ sign
- AND the domain part MUST be fully masked

#### Scenario: Mask API key or token
- GIVEN a `DefaultMaskingStrategy`
- WHEN a string longer than 8 characters is masked as an API key
- THEN the result MUST reveal at most the first 4 characters
- AND the rest MUST be replaced with a fixed mask character

#### Scenario: Mask generic value
- GIVEN a `DefaultMaskingStrategy`
- WHEN a short string (4 characters or fewer) is masked
- THEN the result MUST be a fixed placeholder

### Requirement: TelegramMessageTemplate

The system MUST provide a `TelegramMessageTemplate` extending `QuestionMessageTemplate` that formats questions for Telegram using MarkdownV2-compatible syntax.

#### Scenario: Telegram template includes MarkdownV2 formatting
- GIVEN a `TelegramMessageTemplate` and a `Question` instance
- WHEN `format(question)` is called
- THEN the result MUST use Telegram MarkdownV2 bold markers for field labels

#### Scenario: Telegram template channel type
- GIVEN a `TelegramMessageTemplate` instance
- THEN `channelType` MUST return "telegram"

### Requirement: DiscordMessageTemplate

The system MUST provide a `DiscordMessageTemplate` extending `QuestionMessageTemplate` that formats questions for Discord using markdown-compatible syntax.

#### Scenario: Discord template includes markdown formatting
- GIVEN a `DiscordMessageTemplate` and a `Question` instance
- WHEN `format(question)` is called
- THEN the result MUST use markdown bold markers for field labels

#### Scenario: Discord template channel type
- GIVEN a `DiscordMessageTemplate` instance
- THEN `channelType` MUST return "discord"

### Requirement: Template-aware delivery channels

The existing delivery channels MUST accept `QuestionMessageTemplate` instances as `RichMessageFormatter` without behavioral change to their public API.

#### Scenario: TelegramDeliveryChannel uses template formatter
- GIVEN a `TelegramDeliveryChannel` constructed with a `TelegramMessageTemplate`
- AND a `Question` with status `WAITING_FOR_ANSWER`
- WHEN `send(question)` is called
- THEN the message text MUST be formatted by the template
- AND the Telegram API request MUST include the templated text

#### Scenario: DiscordDeliveryChannel uses template formatter
- GIVEN a `DiscordDeliveryChannel` constructed with a `DiscordMessageTemplate`
- AND a `Question` with status `WAITING_FOR_ANSWER`
- WHEN `send(question)` is called
- THEN the message content MUST be formatted by the template
- AND the Discord webhook request MUST include the templated content

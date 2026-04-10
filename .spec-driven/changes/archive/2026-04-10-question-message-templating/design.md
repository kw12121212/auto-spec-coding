# Design: question-message-templating

## Approach

Add a templating layer between `RichMessageFormatter` and the delivery channels. The design introduces three new types in `org.specdriven.agent.question`:

1. **`QuestionMessageTemplate`** — a record that holds the formatting logic for a single channel. It takes a `Question`, applies field policy and masking, and produces a formatted string for that channel. Each template declares which channel type it targets.

2. **`TemplateFieldPolicy`** — an enum per field controlling inclusion: `INCLUDE`, `TRIM` (omit from output), `MASK` (apply masking strategy). Templates define a default policy map and callers can override per-field.

3. **`MaskingStrategy`** — a functional interface `String mask(String fieldName, String value)` that transforms sensitive values. A `DefaultMaskingStrategy` is provided covering common patterns.

The existing `RichMessageFormatter` interface stays unchanged. `QuestionMessageTemplate` implements `RichMessageFormatter`, so delivery channels can accept a template wherever they currently accept a formatter. `PlainTextFormatter` remains the universal fallback.

`TelegramDeliveryChannel` and `DiscordDeliveryChannel` constructors gain overloaded variants that accept a `QuestionMessageTemplate`. The existing single-arg constructors continue to use `PlainTextFormatter.INSTANCE`.

## Key Decisions

1. **Template implements RichMessageFormatter** — avoids breaking the existing formatter contract. Channels don't need to know whether they received a plain formatter or a template.

2. **Field policy as an enum map, not a DSL** — keeps the design simple and type-safe. No template engine or string interpolation. A code-defined map is sufficient for the first version.

3. **MaskingStrategy as a functional interface** — allows channels or integrators to plug in custom masking (e.g., redact session IDs, mask emails). `DefaultMaskingStrategy` covers common cases with prefix-reveal masking.

4. **Default copy for missing fields** — when a field is null or empty in the `Question`, the template substitutes configurable default text (e.g., "N/A") rather than producing an empty section.

5. **No template files** — first version uses Java records and code. Template files (YAML/JSON) can be added in a future change without changing this API.

## Alternatives Considered

- **Mustache/Handlebars template engine** — rejected as over-engineering for the current two-channel, fixed-format use case. Would add a dependency for minimal benefit. Can be introduced later behind `MaskingStrategy` if needed.

- **Separate per-channel formatter classes only** — the existing `RichMessageFormatter` already supports this pattern (implement the interface per channel). Rejected because it doesn't solve field policy, masking, or default copy in a reusable way — each formatter would duplicate that logic.

- **Modify `Question.toPayload()` to support field filtering** — rejected because `toPayload()` is a canonical representation used by audit, logging, and events. Templating is a presentation concern and should not affect the data model.

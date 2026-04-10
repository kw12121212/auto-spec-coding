# Design: mobile-delivery-observability

## Approach

Add a decorator-based retry layer over existing channels and persist every delivery attempt to a Lealone table. Emit new delivery-specific events on the existing EventBus. Expose a read-only HTTP endpoint for querying delivery status by questionId.

### New types (all in `org.specdriven.agent.question`)

1. **`DeliveryStatus`** enum — `PENDING`, `SENT`, `FAILED`, `RETRYING`
2. **`DeliveryAttempt`** record — `questionId`, `channelType`, `attemptNumber`, `status`, `statusCode` (nullable int), `errorMessage` (nullable), `attemptedAt`
3. **`DeliveryLogStore`** interface — `save(attempt)`, `findByQuestion(questionId)`, `findLatestByQuestion(questionId)`
4. **`LealoneDeliveryLogStore`** — backed by a `delivery_log` table, auto-created on init
5. **`RetryConfig`** record — `maxAttempts` (default 3), `initialDelayMs` (default 1000), `backoffMultiplier` (default 2.0)
6. **`RetryingDeliveryChannel`** — decorator implementing `QuestionDeliveryChannel`, wraps any channel, logs each attempt to `DeliveryLogStore`, emits delivery events, retries on `MobileAdapterException` up to `maxAttempts`

### Event extensions

Add to `EventType` or use the existing extensible event model:
- `DELIVERY_ATTEMPTED` — emitted before each send attempt
- `DELIVERY_SUCCEEDED` — emitted on first successful send
- `DELIVERY_FAILED` — emitted after all retries exhausted

### HTTP endpoint

Add `GET /api/v1/delivery/status/{questionId}` to `HttpApiServlet`. Returns a JSON array of `DeliveryAttempt` records for the given question. Protected by auth filter (unlike callback endpoints).

### Integration point

`BuiltinMobileAdapters.registerAll()` will wrap each assembled `QuestionDeliveryChannel` with `RetryingDeliveryChannel` before returning the handle, passing a shared `DeliveryLogStore` and `RetryConfig`.

## Key Decisions

1. **Decorator over wrapper class** — `RetryingDeliveryChannel` decorates any `QuestionDeliveryChannel`, so retry applies uniformly without modifying Telegram/Discord channel code.
2. **Lealone table for delivery log** — follows the same pattern as `LealoneQuestionStore` and `LealoneAuditLogStore`, auto-creating the table on construction.
3. **Retry on `MobileAdapterException` only** — only channel-specific failures are retried; other runtime exceptions propagate immediately.
4. **Delivery events are separate from question events** — `DELIVERY_*` events are new types, not extensions of `QUESTION_*`. They carry `questionId` in metadata for correlation.
5. **HTTP status endpoint requires auth** — delivery logs contain operational data; callback endpoints remain auth-bypassed as before.

## Alternatives Considered

- **Per-channel retry logic** — rejected: duplicates retry code across Telegram, Discord, and future adapters. Decorator centralizes it.
- **In-memory delivery log only** — rejected: delivery failures are the primary debugging scenario; they must survive restarts.
- **Event-only observability (no table)** — rejected: querying event history for delivery status is fragile; a dedicated table enables efficient lookup by questionId.
- **Circuit breaker pattern** — out of scope for this change; channel health monitoring is a separate concern.

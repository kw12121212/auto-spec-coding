# Tasks: mobile-delivery-observability

## Implementation

- [x] Define `DeliveryStatus` enum in `org.specdriven.agent.question` with values: PENDING, SENT, FAILED, RETRYING
- [x] Define `DeliveryAttempt` record in `org.specdriven.agent.question` with fields: questionId, channelType, attemptNumber, status, statusCode, errorMessage, attemptedAt
- [x] Define `RetryConfig` record in `org.specdriven.agent.question` with fields: maxAttempts (default 3), initialDelayMs (default 1000), backoffMultiplier (default 2.0), with validation (maxAttempts >= 1, initialDelayMs > 0, backoffMultiplier >= 1.0)
- [x] Define `DeliveryLogStore` interface in `org.specdriven.agent.question` with methods: save(DeliveryAttempt), findByQuestion(String questionId), findLatestByQuestion(String questionId)
- [x] Implement `LealoneDeliveryLogStore` backed by a `delivery_log` table (auto-created), following LealoneQuestionStore patterns
- [x] Add delivery event types (DELIVERY_ATTEMPTED, DELIVERY_SUCCEEDED, DELIVERY_FAILED) to the event system, either as EventType enum additions or as typed event constants
- [x] Implement `RetryingDeliveryChannel` decorator: wraps a QuestionDeliveryChannel, catches MobileAdapterException, retries up to maxAttempts with exponential backoff, logs each attempt to DeliveryLogStore, emits delivery events via EventBus
- [x] Integrate `RetryingDeliveryChannel` into `BuiltinMobileAdapters.registerAll()` to wrap each assembled channel
- [x] Add `GET /api/v1/delivery/status/{questionId}` endpoint to HttpApiServlet, returning delivery attempts as JSON, protected by auth filter
- [x] Write delta spec file `changes/mobile-delivery-observability/specs/question-resolution.md` with new observable requirements

## Testing

- [x] Run `mvn compile -q` — lint/validation: verify all new code compiles without errors
- [x] Run `mvn test -pl . -Dtest="DeliveryStatusTest,DeliveryAttemptTest,RetryConfigTest,RetryingDeliveryChannelTest,LealoneDeliveryLogStoreTest"` — unit tests covering enum/record construction, retry logic, persistence, and event emission
- [x] Run `mvn test -pl . -Dtest="HttpCallbackEndpointTest"` — verify existing callback tests still pass and new delivery status endpoint returns correct responses

## Verification

- [x] Verify all delivery events are emitted and contain correct metadata (questionId, channelType, attemptNumber, status)
- [x] Verify retry stops after maxAttempts and emits DELIVERY_FAILED
- [x] Verify delivery status endpoint returns empty array for unknown questionId
- [x] Verify existing question lifecycle and callback tests are unaffected

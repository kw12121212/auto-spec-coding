# Question Resolution Spec — Delta: mobile-delivery-observability

## ADDED Requirements

### Requirement: DeliveryStatus enum

The system MUST define a `DeliveryStatus` enum in `org.specdriven.agent.question` for tracking the outcome of each channel send attempt.

#### Scenario: Required delivery statuses
- THEN `DeliveryStatus` MUST include `PENDING`
- AND `DeliveryStatus` MUST include `SENT`
- AND `DeliveryStatus` MUST include `FAILED`
- AND `DeliveryStatus` MUST include `RETRYING`

### Requirement: DeliveryAttempt record

The system MUST define a `DeliveryAttempt` record in `org.specdriven.agent.question` capturing a single delivery attempt.

#### Scenario: Attempt contains required fields
- GIVEN a `DeliveryAttempt` instance
- THEN it MUST expose `questionId` (String)
- AND it MUST expose `channelType` (String)
- AND it MUST expose `attemptNumber` (int, >= 1)
- AND it MUST expose `status` (DeliveryStatus)
- AND it MUST expose `statusCode` (Integer, nullable)
- AND it MUST expose `errorMessage` (String, nullable)
- AND it MUST expose `attemptedAt` (long, epoch millis)

### Requirement: RetryConfig record

The system MUST define a `RetryConfig` record in `org.specdriven.agent.question` for configuring delivery retry behavior.

#### Scenario: Config has defaults
- GIVEN a `RetryConfig` constructed with no arguments
- THEN `maxAttempts` MUST be 3
- AND `initialDelayMs` MUST be 1000
- AND `backoffMultiplier` MUST be 2.0

#### Scenario: Config validates maxAttempts
- GIVEN a `RetryConfig` with `maxAttempts` less than 1
- WHEN constructed
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: Config validates initialDelayMs
- GIVEN a `RetryConfig` with `initialDelayMs` less than or equal to 0
- WHEN constructed
- THEN it MUST throw `IllegalArgumentException`

#### Scenario: Config validates backoffMultiplier
- GIVEN a `RetryConfig` with `backoffMultiplier` less than 1.0
- WHEN constructed
- THEN it MUST throw `IllegalArgumentException`

### Requirement: DeliveryLogStore interface

The system MUST define a `DeliveryLogStore` interface in `org.specdriven.agent.question` for persisting delivery attempts.

#### Scenario: Save delivery attempt
- GIVEN a `DeliveryAttempt` instance
- WHEN `save(attempt)` is called
- THEN the attempt MUST be persisted

#### Scenario: Find attempts by question
- GIVEN multiple delivery attempts for a questionId
- WHEN `findByQuestion(questionId)` is called
- THEN it MUST return all attempts for that questionId ordered by attemptNumber

#### Scenario: Find latest attempt
- GIVEN multiple delivery attempts for a questionId
- WHEN `findLatestByQuestion(questionId)` is called
- THEN it MUST return the attempt with the highest attemptNumber

#### Scenario: Return empty for unknown question
- GIVEN no delivery attempts for a questionId
- WHEN `findByQuestion(questionId)` is called
- THEN it MUST return an empty list

### Requirement: LealoneDeliveryLogStore

The system MUST provide a `LealoneDeliveryLogStore` implementing `DeliveryLogStore` backed by a Lealone SQL table.

#### Scenario: Auto-create table on init
- GIVEN a fresh database
- WHEN `LealoneDeliveryLogStore` is constructed
- THEN a `delivery_log` table MUST be created automatically

### Requirement: Delivery lifecycle events

The system MUST emit events for delivery attempt outcomes.

#### Scenario: Required delivery event types
- THEN the event model MUST include `DELIVERY_ATTEMPTED`
- AND it MUST include `DELIVERY_SUCCEEDED`
- AND it MUST include `DELIVERY_FAILED`

#### Scenario: Delivery-attempted event metadata
- GIVEN a `DELIVERY_ATTEMPTED` event
- THEN its metadata MUST include `questionId`, `channelType`, and `attemptNumber`

#### Scenario: Delivery-succeeded event metadata
- GIVEN a `DELIVERY_SUCCEEDED` event
- THEN its metadata MUST include `questionId`, `channelType`, and `attemptNumber`

#### Scenario: Delivery-failed event metadata
- GIVEN a `DELIVERY_FAILED` event
- THEN its metadata MUST include `questionId`, `channelType`, `attemptNumber`, and `errorMessage`

### Requirement: RetryingDeliveryChannel

The system MUST provide a `RetryingDeliveryChannel` implementing `QuestionDeliveryChannel` that decorates another channel with retry logic.

#### Scenario: Successful first attempt
- GIVEN a `RetryingDeliveryChannel` wrapping a channel that succeeds
- WHEN `send(question)` is called
- THEN the underlying channel MUST be called once
- AND a `DeliveryAttempt` with status `SENT` MUST be logged
- AND a `DELIVERY_SUCCEEDED` event MUST be emitted

#### Scenario: Retry on transient failure
- GIVEN a `RetryingDeliveryChannel` with `maxAttempts == 3`
- AND a channel that fails on the first two attempts and succeeds on the third
- WHEN `send(question)` is called
- THEN the underlying channel MUST be called 3 times
- AND `DeliveryAttempt` records MUST be logged for each attempt
- AND the first two attempts MUST have status `RETRYING`
- AND the final attempt MUST have status `SENT`
- AND a `DELIVERY_SUCCEEDED` event MUST be emitted

#### Scenario: Exhausted retries
- GIVEN a `RetryingDeliveryChannel` with `maxAttempts == 2`
- AND a channel that always throws `MobileAdapterException`
- WHEN `send(question)` is called
- THEN the underlying channel MUST be called 2 times
- AND a `DELIVERY_FAILED` event MUST be emitted
- AND a `MobileAdapterException` MUST be thrown

#### Scenario: Non-adapter exception propagates immediately
- GIVEN a `RetryingDeliveryChannel` wrapping a channel that throws `NullPointerException`
- WHEN `send(question)` is called
- THEN the exception MUST propagate without retry
- AND no `DELIVERY_FAILED` event MUST be emitted

#### Scenario: Exponential backoff between retries
- GIVEN a `RetryingDeliveryChannel` with `initialDelayMs == 100` and `backoffMultiplier == 2.0`
- AND a channel that fails twice then succeeds
- WHEN `send(question)` is called
- THEN the delay before the second attempt MUST be approximately 100ms
- AND the delay before the third attempt MUST be approximately 200ms

### Requirement: RetryingDeliveryChannel integration

The system MUST wrap assembled delivery channels with retry in `BuiltinMobileAdapters`.

#### Scenario: Built-in adapters use retry wrapper
- GIVEN an empty `MobileChannelRegistry`, a `QuestionRuntime`, a `SecretVault`, a `DeliveryLogStore`, and a `RetryConfig`
- WHEN `BuiltinMobileAdapters.registerAll(registry, runtime, vault, logStore, retryConfig)` is called
- THEN each assembled `MobileChannelHandle` MUST contain a `RetryingDeliveryChannel` wrapping the original channel

### Requirement: Delivery status HTTP endpoint

The system MUST expose an HTTP endpoint for querying delivery status.

#### Scenario: Query delivery status by question
- GIVEN a running HTTP server with delivery log entries for questionId "q1"
- WHEN `GET /api/v1/delivery/status/q1` is requested with valid auth
- THEN the server MUST respond with HTTP 200
- AND the response body MUST be a JSON array of delivery attempts for that question

#### Scenario: Empty result for unknown question
- GIVEN a running HTTP server with no delivery entries for questionId "q99"
- WHEN `GET /api/v1/delivery/status/q99` is requested with valid auth
- THEN the server MUST respond with HTTP 200
- AND the response body MUST be an empty JSON array

#### Scenario: Delivery status endpoint requires auth
- GIVEN a running HTTP server with auth middleware
- WHEN `GET /api/v1/delivery/status/q1` is requested without auth
- THEN the server MUST respond with HTTP 401

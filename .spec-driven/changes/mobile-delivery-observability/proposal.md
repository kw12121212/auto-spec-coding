# mobile-delivery-observability

## What

Add delivery-level observability to the mobile interaction channels: track each delivery attempt with status and failure reason, emit structured lifecycle events, persist delivery logs to Lealone, and provide an HTTP endpoint for querying delivery status. Wrap existing channels with a retry decorator that respects configurable backoff and max-attempt limits.

## Why

M23's four previous changes built the full mobile push/reply pipeline (config registry, adapters, templating, callback routing) but left delivery operators blind: there is no record of whether a message reached the channel, no retry on transient failures, and no way to inspect delivery status after the fact. Without observability, operators cannot diagnose why a question went unanswered or know which channels are failing.

## Scope

- `DeliveryStatus` enum and `DeliveryAttempt` record for per-attempt tracking
- `RetryingDeliveryChannel` decorator wrapping any `QuestionDeliveryChannel` with configurable retry
- `DeliveryLogStore` interface and `LealoneDeliveryLogStore` for persistent delivery attempt records
- Delivery lifecycle events: `DELIVERY_ATTEMPTED`, `DELIVERY_SUCCEEDED`, `DELIVERY_FAILED`
- HTTP endpoint `GET /api/v1/delivery/status/{questionId}` returning delivery status
- Integration with existing `BuiltinMobileAdapters` to wrap channels with retry decorator

## Unchanged Behavior

- Existing channel send/reply semantics remain identical when delivery succeeds on first attempt
- Callback endpoint routing and signature validation unchanged
- Question lifecycle events (CREATED, ANSWERED, EXPIRED, ESCALATED) unchanged
- Templating and field policies unchanged

# Design: go-sdk-events

## Approach

Implement event subscription as bounded polling rather than SSE.

On the Java side, `HttpApiServlet` will observe the `SpecDriven` SDK event bus and maintain a small in-memory event buffer for events seen by that servlet instance. `GET /api/v1/events` will return retained events ordered by a monotonically increasing sequence cursor. The endpoint will accept optional `after`, `limit`, and `type` query parameters:

- `after` returns only events with sequence greater than the supplied cursor.
- `limit` bounds the number of returned events.
- `type` narrows results to one `EventType`.

The response will include the returned events and a `nextCursor` value that Go callers can feed into the next poll.

On the Go side, add `events.go` with public event payload types, poll options, an `Events` facade, and a polling subscription helper. The low-level `Client` will perform the HTTP request and decode the response. The high-level facade will provide one-shot polling and a callback-based subscribe loop that updates the cursor after delivered events and stops when the caller's context is canceled.

Tests stay unit-level for this change: Java servlet tests verify route behavior with a real SDK event bus and Go `httptest` tests verify request shape, response decoding, cursor advancement, callback order, auth, error propagation, and cancellation.

## Key Decisions

- Use polling for the first event subscription surface. M20 explicitly allows SSE or polling, and polling is easier to test and operate without adding long-lived servlet response handling.
- Use an in-memory HTTP event buffer. This satisfies remote SDK subscription needs without adding persistence scope that belongs to audit-log or integration-test work.
- Use a server-assigned sequence cursor rather than timestamp-only polling. Multiple events can share timestamps, while a sequence cursor gives deterministic ordering and deduplication.
- Keep event payloads aligned with the existing Java `Event` model: type, timestamp, source, and metadata. The HTTP wrapper adds only transport cursor fields.
- Require authentication through the existing HTTP filter chain. `/events` is not a health route and can expose operational metadata.
- Keep Go subscription callback-based. It avoids forcing a channel ownership model on callers while still supporting ordinary context cancellation.

## Alternatives Considered

- SSE streaming was ruled out for this change because it requires long-lived response lifecycle behavior and reconnection semantics. It can be added later without invalidating the polling API.
- Persisting events through `AuditLogStore` was ruled out because M20 only needs SDK event subscription, not durable audit replay.
- Returning raw Java event JSON without a cursor wrapper was ruled out because clients need a stable way to resume without duplicates.
- Starting with TypeScript SDK events was ruled out because M20 is already partially complete and should close its own event surface before M21 copies the pattern.

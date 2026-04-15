# Tasks: go-sdk-events

## Implementation

- [x] Expose the SDK event bus to the HTTP layer without changing existing SDK listener behavior.
- [x] Add an in-memory HTTP event buffer that records events with monotonically increasing sequence cursors.
- [x] Add `GET /api/v1/events` routing, query validation, event-type filtering, cursor filtering, limit handling, and JSON response encoding.
- [x] Add Go SDK event payload, polling request, and polling response models.
- [x] Add a low-level Go `Client` event polling method that calls `GET /api/v1/events`.
- [x] Add a high-level Go `Events` facade with one-shot polling and callback-based polling subscription.
- [x] Preserve existing Go SDK client, agent, and tools behavior.

## Testing

- [x] Add Java unit tests for event route auth-visible behavior, cursor ordering, `after`, `limit`, `type`, invalid query values, and empty result handling.
- [x] Add Go unit tests for event polling request path/query/auth, response decoding, API error preservation, type filtering, and context cancellation.
- [x] Add Go unit tests for polling subscription callback order, cursor advancement, duplicate avoidance, and cancellation stop behavior.
- [x] Run Go formatting validation: `cd go-sdk && test -z "$(gofmt -l .)"`
- [x] Run Go unit tests: `cd go-sdk && go test ./...`
- [x] Run Java unit tests for affected HTTP behavior: `mvn -Dtest=HttpApiServletTest test`
- [x] Run Java compile validation: `mvn -DskipTests compile`

## Verification

- [x] Verify implementation matches the delta spec requirements.
- [x] Verify `/api/v1/events` does not change existing HTTP route behavior.
- [x] Verify the Go subscriber stops cleanly when its context is canceled.
- [x] Run spec-driven verification for this change: `node /home/wx766/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify go-sdk-events`

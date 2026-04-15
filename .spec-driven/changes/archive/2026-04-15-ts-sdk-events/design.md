# Design: ts-sdk-events

## Approach

- Keep the backend contract unchanged and implement the roadmap item entirely inside `sdk/ts/`.
- Add a small `events.ts` module that builds a polling-backed subscription helper on top of `SpecDrivenClient.pollEvents()`.
- Define explicit subscription option types in the TypeScript SDK so callers can choose an initial cursor, limit, event-type filter, and polling interval.
- Export the new helper and its types from `sdk/ts/src/index.ts` so the public package surface remains centralized.
- Verify behavior with focused unit tests that exercise cursor progression, empty poll handling, stop behavior, interval-driven repeated polling, and failure propagation.

## Key Decisions

- Build on HTTP polling rather than SSE because the existing Java HTTP API spec already guarantees `GET /api/v1/events`, while SSE is only mentioned as a roadmap possibility and is not a current backend contract.
- Keep `pollEvents()` as the primitive transport API and layer subscription ergonomics on top of it, preserving backward compatibility for callers that want direct request control.
- Treat this change as SDK-surface completion, not integration-test expansion; the remaining `ts-sdk-tests` roadmap item is the better place for broader real-backend coverage.
- Keep the proposed surface minimal: one polling-backed helper and related option types, instead of introducing a larger reactive or event-emitter abstraction family.

## Alternatives Considered

- Add SSE support now. Rejected because no repository evidence shows an implemented SSE backend route or spec requirement, so the proposal would either invent backend scope or leave the SDK coupled to a nonexistent transport.
- Make `ts-sdk-tests` the next change instead. Rejected because tests should target a settled event API surface instead of preceding it.
- Expand into a generic observable/stream abstraction. Rejected because it would add avoidable API and maintenance surface before concrete need is proven.

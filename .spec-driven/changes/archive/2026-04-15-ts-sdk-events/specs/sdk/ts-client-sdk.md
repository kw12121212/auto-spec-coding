---
mapping:
  implementation:
    - sdk/ts/src/client.ts
    - sdk/ts/src/index.ts
    - sdk/ts/src/models.ts
    - sdk/ts/src/events.ts
  tests:
    - sdk/ts/src/client.test.ts
    - sdk/ts/src/events.test.ts
---

## ADDED Requirements

### Requirement: TypeScript SDK event subscription helper
The TypeScript client SDK MUST provide a polling-backed event subscription helper for Node.js callers that continuously consumes backend events through the existing `GET /api/v1/events` endpoint.

#### Scenario: Subscription starts from current cursor
- GIVEN a configured client and a caller-created event subscription
- WHEN the caller starts consuming events without providing an explicit cursor
- THEN the subscription MUST begin polling through the existing `pollEvents()` client behavior
- AND each received event MUST preserve the backend event payload fields already exposed by the SDK

#### Scenario: Subscription resumes from explicit cursor
- GIVEN a configured client and a caller-created event subscription with an explicit `after` cursor
- WHEN the subscription performs its first poll
- THEN the first request MUST use that cursor value
- AND later polls MUST continue from the most recent returned `nextCursor`

#### Scenario: Empty poll result advances without emitting events
- GIVEN a running subscription and a poll result with no events
- WHEN the backend returns an empty `events` array and a reusable `nextCursor`
- THEN the subscription MUST not emit synthetic events
- AND the subscription MUST continue polling from the returned cursor

#### Scenario: Subscription stops cleanly
- GIVEN a running subscription
- WHEN the caller stops or closes it
- THEN no further polling requests MUST be issued for that subscription

### Requirement: TypeScript SDK event subscription controls
The TypeScript client SDK MUST let callers control polling cadence and lifecycle without reimplementing the poll loop themselves.

#### Scenario: Subscription uses configured polling interval
- GIVEN a caller configures a polling interval for a subscription
- WHEN the subscription runs
- THEN successive polls MUST wait for approximately that interval between completed polling cycles

#### Scenario: Subscription surfaces polling failure
- GIVEN a running subscription
- WHEN an underlying `pollEvents()` request fails
- THEN the subscription MUST surface that failure to the caller
- AND the subscription MUST stop continuing silently with an unknown state

## MODIFIED Requirements

### Requirement: TypeScript SDK event polling method
Previously: The TypeScript client SDK MUST provide a low-level method for polling backend events through `GET /api/v1/events`.
The TypeScript client SDK MUST provide a low-level method for polling backend events through `GET /api/v1/events` and MAY expose higher-level polling-backed subscription helpers built on that method without changing its existing observable request/response behavior.

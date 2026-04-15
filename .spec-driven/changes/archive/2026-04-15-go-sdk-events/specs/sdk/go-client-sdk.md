---
mapping:
  implementation:
    - go-sdk/specdriven/client.go
    - go-sdk/specdriven/events.go
  tests:
    - go-sdk/specdriven/events_test.go
---

## ADDED Requirements

### Requirement: Go SDK event models
The Go client SDK MUST expose Go types for event polling responses returned by the Java backend HTTP REST API.

#### Scenario: Event payload decodes supported fields
- GIVEN a Java HTTP API event response containing `sequence`, `type`, `timestamp`, `source`, and `metadata`
- WHEN the Go SDK decodes the response
- THEN the Go event value MUST expose those fields to the caller

#### Scenario: Empty event list decodes as empty slice
- GIVEN a Java HTTP API event response with no events
- WHEN the Go SDK decodes the response
- THEN the Go response MUST expose an empty event slice rather than nil

### Requirement: Go SDK event polling client
The Go client SDK MUST provide a low-level client method for polling backend events through `GET /api/v1/events`.

#### Scenario: Poll events sends cursor and limit
- GIVEN a configured Go SDK client and event poll options with an `after` cursor and limit
- WHEN the caller polls events
- THEN the SDK MUST send `GET /api/v1/events?after=<cursor>&limit=<limit>`
- AND return the decoded event polling response

#### Scenario: Poll events sends type filter
- GIVEN a configured Go SDK client and an event type filter
- WHEN the caller polls events
- THEN the SDK MUST include the requested event type in the event polling query

#### Scenario: Poll events uses authentication
- GIVEN a Go SDK client configured with HTTP API credentials
- WHEN the caller polls events
- THEN the request MUST include the same authentication headers used by other authenticated SDK methods

#### Scenario: Poll events preserves API errors
- GIVEN the Java backend rejects an event polling request
- WHEN the caller polls events
- THEN the returned error MUST preserve the typed API error details exposed by the existing HTTP client

### Requirement: Go SDK Events facade
The Go client SDK MUST provide a high-level Events API that wraps backend event polling.

#### Scenario: Events facade can be constructed from client
- GIVEN a configured Go SDK HTTP client
- WHEN a Go caller constructs an Events handle from that client
- THEN the Events handle MUST be ready to poll and subscribe to backend events

#### Scenario: Events facade rejects nil client
- GIVEN no Go SDK HTTP client
- WHEN a Go caller constructs an Events handle
- THEN construction MUST fail with a validation error

#### Scenario: Events poll delegates to client
- GIVEN an Events handle
- WHEN the Go caller polls events
- THEN the SDK MUST call the existing event polling client method
- AND return the decoded event polling response

### Requirement: Go SDK polling subscription
The Go client SDK MUST provide a polling subscription helper for receiving backend events until the caller stops it.

#### Scenario: Subscription delivers events in order
- GIVEN the backend returns multiple events across polling responses
- WHEN the caller starts a polling subscription
- THEN the SDK MUST deliver events to the caller callback in ascending sequence order

#### Scenario: Subscription advances cursor
- GIVEN the backend returns events with increasing sequence values
- WHEN the subscription performs its next poll
- THEN the SDK MUST use the latest delivered sequence as the next `after` cursor

#### Scenario: Subscription avoids duplicate delivery
- GIVEN a later backend polling response includes an event sequence that was already delivered
- WHEN the subscription processes that response
- THEN the SDK MUST NOT deliver the duplicate event again

#### Scenario: Subscription stops on context cancellation
- GIVEN a running polling subscription
- WHEN the caller cancels the context
- THEN the subscription MUST stop polling and return a context cancellation error or nil cancellation result according to its documented contract

#### Scenario: Subscription returns polling errors
- GIVEN the backend returns an API or transport error while a subscription is polling
- WHEN the error occurs
- THEN the subscription MUST return that error to the caller instead of silently dropping it

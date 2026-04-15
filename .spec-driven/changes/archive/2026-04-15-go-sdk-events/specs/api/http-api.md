---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/http/HttpApiServlet.java
    - src/main/java/org/specdriven/sdk/SpecDriven.java
  tests:
    - src/test/java/org/specdriven/agent/http/HttpApiServletTest.java
---

## ADDED Requirements

### Requirement: HTTP event polling endpoint
The HTTP REST API MUST expose an authenticated event polling endpoint at `GET /api/v1/events`.

#### Scenario: Poll events returns retained events
- GIVEN the HTTP API has observed backend events after servlet initialization
- WHEN an authenticated caller sends `GET /api/v1/events`
- THEN the response MUST contain the retained events in ascending sequence order
- AND each returned event MUST include `sequence`, `type`, `timestamp`, `source`, and `metadata`

#### Scenario: Poll events uses after cursor
- GIVEN the HTTP API has retained events with sequences greater than and less than a cursor value
- WHEN an authenticated caller sends `GET /api/v1/events?after=<cursor>`
- THEN the response MUST include only events whose sequence is greater than `<cursor>`

#### Scenario: Poll events returns next cursor
- GIVEN an authenticated caller polls events
- WHEN the endpoint returns successfully
- THEN the response MUST include `nextCursor`
- AND `nextCursor` MUST be the greatest returned event sequence when events are returned
- AND `nextCursor` MUST remain usable as the next `after` cursor when no events are returned

#### Scenario: Poll events filters by type
- GIVEN the HTTP API has retained events with different event types
- WHEN an authenticated caller sends `GET /api/v1/events?type=AGENT_STATE_CHANGED`
- THEN the response MUST include only retained events with that type

#### Scenario: Poll events applies limit
- GIVEN the HTTP API has retained more events than the requested limit
- WHEN an authenticated caller sends `GET /api/v1/events?limit=2`
- THEN the response MUST include no more than two events
- AND the returned events MUST be the earliest matching events after the cursor

#### Scenario: Invalid event query returns 400
- GIVEN a caller supplies an invalid `after`, `limit`, or `type` query value
- WHEN the event polling endpoint processes the request
- THEN an `ErrorResponse` with `status=400` and `error="invalid_params"` MUST be returned

### Requirement: HTTP event polling compatibility
The event polling endpoint MUST preserve existing HTTP REST API behavior.

#### Scenario: Existing routes remain compatible
- GIVEN callers use `/health`, `/tools`, `/agent/run`, `/agent/stop`, `/agent/state`, `/callbacks/{channel}`, or `/delivery/status/{questionId}`
- WHEN the event polling endpoint is available
- THEN those existing routes MUST keep their existing observable behavior

#### Scenario: Health remains unauthenticated
- GIVEN `/api/v1/events` requires authentication through the normal filter chain
- WHEN a caller sends `GET /api/v1/health`
- THEN health endpoint authentication behavior MUST remain unchanged

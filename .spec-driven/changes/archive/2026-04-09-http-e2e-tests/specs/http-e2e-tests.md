# http-e2e-tests.md

## ADDED Requirements

### Requirement: HTTP end-to-end test stack

The system MUST provide an `HttpTestStack` test fixture that starts an embedded Tomcat server, registers `AuthFilter`, `RateLimitFilter`, and `HttpApiServlet` on `/api/v1/*`, and exposes a base URL for sending real HTTP requests.

#### Scenario: Test stack starts and stops cleanly
- GIVEN an `HttpTestStack` with a test API key and low rate-limit threshold
- WHEN the stack is started and then stopped
- THEN the embedded server MUST be accessible during the started phase and MUST release its port after stopping

### Requirement: Health endpoint e2e test

The `GET /api/v1/health` endpoint MUST return a valid health response without authentication through the full HTTP stack.

#### Scenario: Health check without auth
- GIVEN a running `HttpTestStack` with no auth credentials provided
- WHEN a GET request is sent to `/api/v1/health`
- THEN the response MUST have status 200 and a JSON body containing `"status":"ok"` and `"version":"0.1.0"`

### Requirement: Authenticated routes e2e test

All non-health routes MUST require authentication through the full filter chain.

#### Scenario: Valid Bearer token grants access
- GIVEN a running `HttpTestStack` with API key `test-api-key`
- WHEN a GET request is sent to `/api/v1/tools` with header `Authorization: Bearer test-api-key`
- THEN the response MUST have status 200

#### Scenario: Valid X-API-Key header grants access
- GIVEN a running `HttpTestStack` with API key `test-api-key`
- WHEN a GET request is sent to `/api/v1/tools` with header `X-API-Key: test-api-key`
- THEN the response MUST have status 200

#### Scenario: Missing auth returns 401
- GIVEN a running `HttpTestStack`
- WHEN a GET request is sent to `/api/v1/tools` without auth headers
- THEN the response MUST have status 401 and a JSON body containing `"error":"unauthorized"`

#### Scenario: Invalid key returns 401
- GIVEN a running `HttpTestStack`
- WHEN a GET request is sent to `/api/v1/tools` with header `Authorization: Bearer wrong-key`
- THEN the response MUST have status 401

### Requirement: Agent lifecycle e2e test

The agent run → state → stop → state flow MUST work end-to-end through real HTTP.

#### Scenario: Full agent lifecycle
- GIVEN a running `HttpTestStack` with valid auth
- WHEN a POST request is sent to `/api/v1/agent/run` with body `{"prompt":"hello"}`
- THEN the response MUST have status 200 with a non-null `agentId` and `state` containing "STOPPED"
- WHEN a GET request is sent to `/api/v1/agent/state?id=<agentId>`
- THEN the response MUST have status 200 with the same `agentId`
- WHEN a POST request is sent to `/api/v1/agent/stop?id=<agentId>`
- THEN the response MUST have status 200
- WHEN a GET request is sent to `/api/v1/agent/state?id=<agentId>` again
- THEN the response MUST have status 200 with `state` containing "STOPPED"

### Requirement: Rate limiting e2e test

The rate-limit filter MUST enforce request limits through the full HTTP stack.

#### Scenario: Over-limit returns 429
- GIVEN a running `HttpTestStack` with rate limit max=5
- WHEN 6 authenticated requests are sent in rapid succession
- THEN the first 5 MUST return status 200
- AND the 6th MUST return status 429 with a `Retry-After` header

### Requirement: Error path e2e tests

Error responses MUST propagate correctly through the full HTTP stack.

#### Scenario: Unknown route returns 404
- GIVEN a running `HttpTestStack` with valid auth
- WHEN a GET request is sent to `/api/v1/nonexistent`
- THEN the response MUST have status 404 and a JSON body containing `"error":"not_found"`

#### Scenario: Wrong method returns 405
- GIVEN a running `HttpTestStack` with valid auth
- WHEN a DELETE request is sent to `/api/v1/health`
- THEN the response MUST have status 405

#### Scenario: Invalid JSON body returns 400
- GIVEN a running `HttpTestStack` with valid auth
- WHEN a POST request is sent to `/api/v1/agent/run` with body `{"systemPrompt":"no prompt"}`
- THEN the response MUST have status 400 and a JSON body containing `"error":"invalid_params"`

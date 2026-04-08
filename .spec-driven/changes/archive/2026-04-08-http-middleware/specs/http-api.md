# http-api.md

## ADDED Requirements

### Requirement: Authentication filter

The system MUST provide `AuthFilter` in `org.specdriven.agent.http` extending `jakarta.servlet.http.HttpFilter`, registered on `/api/v1/*`.

#### Scenario: Valid Bearer token passes auth
- GIVEN a request with header `Authorization: Bearer <valid-key>`
- WHEN `doFilter()` is called
- THEN the filter MUST call `chain.doFilter()` to continue processing

#### Scenario: Valid X-API-Key header passes auth
- GIVEN a request with header `X-API-Key: <valid-key>`
- WHEN `doFilter()` is called
- THEN the filter MUST call `chain.doFilter()` to continue processing

#### Scenario: Missing auth header returns 401
- GIVEN a request without `Authorization` or `X-API-Key` header
- WHEN `doFilter()` is called
- THEN an `ErrorResponse` with `status=401` and `error="unauthorized"` MUST be returned
- AND `chain.doFilter()` MUST NOT be called

#### Scenario: Invalid API key returns 401
- GIVEN a request with header `Authorization: Bearer invalid-key`
- WHEN `doFilter()` is called
- THEN an `ErrorResponse` with `status=401` and `error="unauthorized"` MUST be returned

#### Scenario: Health endpoint bypasses auth
- GIVEN a GET request to `/api/v1/health` without any auth header
- WHEN `doFilter()` is called
- THEN the filter MUST call `chain.doFilter()` without checking credentials

### Requirement: Rate limit filter

The system MUST provide `RateLimitFilter` in `org.specdriven.agent.http` extending `jakarta.servlet.http.HttpFilter`, registered on `/api/v1/*`.

#### Scenario: Requests under limit pass through
- GIVEN a client making requests within the configured rate limit
- WHEN `doFilter()` is called
- THEN the filter MUST call `chain.doFilter()` to continue processing

#### Scenario: Requests over limit return 429
- GIVEN a client has exceeded the configured request limit within the current window
- WHEN `doFilter()` is called
- THEN an `ErrorResponse` with `status=429` and `error="rate_limited"` MUST be returned
- AND a `Retry-After` header MUST be set with the remaining seconds in the current window

#### Scenario: Window reset allows new requests
- GIVEN a client was rate-limited in a previous window
- AND the window has expired
- WHEN the client makes a new request
- THEN the filter MUST call `chain.doFilter()` to continue processing

### Requirement: Middleware error response format

All middleware error responses MUST use `HttpJsonCodec.encode(ErrorResponse)` with `Content-Type: application/json; charset=utf-8`, matching the existing servlet error format.

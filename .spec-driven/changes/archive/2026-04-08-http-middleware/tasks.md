# Tasks: http-middleware

## Implementation

- [x] Create `AuthFilter` extending `jakarta.servlet.http.HttpFilter` in `org.specdriven.agent.http` that validates API key from `Authorization: Bearer <key>` or `X-API-Key` header, bypasses `/health`, and returns 401 `ErrorResponse` on failure
- [x] Create `RateLimitFilter` extending `jakarta.servlet.http.HttpFilter` in `org.specdriven.agent.http` that enforces per-client fixed-window rate limits, returns 429 `ErrorResponse` with `Retry-After` header on threshold breach
- [x] Add delta spec requirements for `AuthFilter` and `RateLimitFilter` to `changes/http-middleware/specs/http-api.md`

## Testing

- [x] Validation: run `mvn compile` to verify no compilation errors
- [x] Unit tests: run `mvn test` to verify all tests pass
- [x] Write `AuthFilterTest` — unit tests covering: valid Bearer token, valid X-API-Key, missing token (401), invalid token (401), `/health` bypass
- [x] Write `RateLimitFilterTest` — unit tests covering: requests under limit pass, requests over limit get 429, `Retry-After` header present on 429, window reset allows new requests

## Verification

- [x] Verify `AuthFilter` returns 401 for unauthenticated requests to all routes except `/health`
- [x] Verify `RateLimitFilter` returns 429 when rate limit exceeded
- [x] Verify `/health` returns 200 without any authentication
- [x] Verify existing `HttpApiServletTest` tests still pass unchanged

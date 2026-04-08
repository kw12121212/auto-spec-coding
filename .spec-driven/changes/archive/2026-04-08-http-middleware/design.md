# Design: http-middleware

## Approach

Implement two independent Jakarta Servlet `HttpFilter` classes registered on `/api/v1/*` via `TomcatRouter.addFilter()`. Filters execute before `HttpApiServlet.service()` in registration order: `AuthFilter` first, then `RateLimitFilter`.

**AuthFilter** reads the API key from either `Authorization: Bearer <key>` or `X-API-Key` header. Valid keys are configured at servlet/filter initialization time. The `/health` path bypasses auth. On failure, the filter writes a 401 JSON `ErrorResponse` directly and does not call `chain.doFilter()`.

**RateLimitFilter** identifies clients by their API key (post-auth). It uses an in-memory `ConcurrentHashMap<String, RateLimitWindow>` with fixed-window counters. When a client exceeds the configured threshold within the window, the filter writes a 429 JSON `ErrorResponse` and sets `Retry-After` header. The window resets after the configured duration.

Both filters use the existing `HttpJsonCodec.encode(ErrorResponse)` for error formatting to stay consistent with the servlet's error response format.

## Key Decisions

1. **Jakarta Servlet Filter over servlet-internal interceptors** — Lealone's `TomcatRouter` supports standard `HttpFilter` registration with URL patterns. This keeps auth/rate-limiting decoupled from `HttpApiServlet` and allows independent testing.

2. **`/health` bypasses auth** — Health checks are used by load balancers and orchestrators that cannot provide API keys. The bypass is implemented in `AuthFilter` by checking `request.getRequestURI()`.

3. **In-memory rate limiting** — No external store dependency. Rate limits reset on process restart, which is acceptable for an embedded agent service. Uses fixed-window algorithm for simplicity.

4. **Dual header support for API key** — Accept both `Authorization: Bearer <key>` (standard) and `X-API-Key` (convenience) to support a wide range of HTTP clients.

5. **Configurable thresholds** — API keys and rate limits are passed as filter init-params or SDK config, not hard-coded.

## Alternatives Considered

- **Servlet-wrapper approach** — Wrapping `HttpApiServlet.service()` with pre-check logic inside the servlet itself. Rejected: couples middleware to servlet, harder to test independently, doesn't compose.
- **Lealone built-in auth** — Lealone doesn't ship a built-in auth middleware for HTTP routes. Would require modifying the Lealone submodule, violating the project constraint.
- **Token bucket rate limiting** — More precise than fixed-window but adds complexity without clear benefit for this use case.

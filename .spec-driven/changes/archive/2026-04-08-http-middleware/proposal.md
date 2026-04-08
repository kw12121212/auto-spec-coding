# http-middleware

## What

Add authentication and rate-limiting middleware to the HTTP REST API layer, implemented as Jakarta Servlet `HttpFilter` instances registered on `/api/v1/*` via Lealone's `TomcatRouter.addFilter()`.

## Why

M14's `http-routes` and `http-models` are complete — the API is functionally working but accepts unauthenticated, unthrottled requests. Before exposing the service over the network, it needs auth and rate limiting to prevent abuse. This is a prerequisite for `http-e2e-tests` which will validate the full stack including middleware.

## Scope

- `AuthFilter`: Jakarta Servlet `HttpFilter` that validates API key from `Authorization: Bearer <key>` or `X-API-Key` header. Rejects unauthenticated requests with 401.
- `RateLimitFilter`: Jakarta Servlet `HttpFilter` that enforces per-client request rate limits using an in-memory fixed-window counter. Rejects over-threshold requests with 429.
- Filter registration on `/api/v1/*` via `TomcatRouter.addFilter()`.
- `/health` endpoint MUST bypass authentication (public health check).
- Delta spec additions to `http-api.md` describing middleware observable behavior.

## Unchanged Behavior

- Existing route handlers (`/agent/run`, `/agent/stop`, `/agent/state`, `/tools`, `/health`) MUST produce the same responses as before when auth and rate-limit checks pass.
- `HttpApiServlet` dispatch logic is not modified — filters run before the servlet.
- Error response format (`ErrorResponse` JSON via `HttpJsonCodec`) is unchanged.

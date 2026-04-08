# http-e2e-tests

## What

Implement end-to-end integration tests for the HTTP REST API layer that validate the full request pipeline: HTTP request → servlet filter chain (auth, rate-limit) → HttpApiServlet → SDK → JSON response. Tests will use an embedded servlet container (Tomcat, already a transitive dependency via lealone-http) and Java's built-in `HttpClient` to exercise real HTTP requests against a live server.

## Why

M14 has three completed changes (`http-routes`, `http-middleware`, `http-models`) each verified by unit tests using stub-based servlet mocks. These tests validate individual components in isolation but do not confirm that the full stack works together — filter ordering, real HTTP serialization/deserialization, content-type headers, and error propagation through the servlet container. E2E tests close this gap and serve as the final validation before marking M14 complete.

## Scope

- Spin up an embedded Tomcat server in a shared `HttpTestStack` test fixture
- Register `AuthFilter`, `RateLimitFilter`, and `HttpApiServlet` on `/api/v1/*`
- Test all routes: `GET /health`, `GET /tools`, `POST /agent/run`, `POST /agent/stop`, `GET /agent/state`
- Test auth filter integration: valid key via Bearer token and X-API-Key header, missing/invalid key rejection, health endpoint bypass
- Test rate-limit filter integration: requests under limit pass, over limit returns 429 with Retry-After header, window reset
- Test error propagation through the full stack: 404 unknown route, 405 wrong method, 400 invalid body
- Test complete agent lifecycle: run → query state → stop → query state again

## Unchanged Behavior

All existing HTTP API behavior must remain unchanged. This change adds tests only — no production code modifications except potentially extracting shared test utilities (e.g., `StubTool`) to a test-helper location if needed.

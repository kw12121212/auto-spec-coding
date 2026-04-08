# Tasks: http-e2e-tests

## Implementation

- [x] Create `HttpE2eTest` class in `org.specdriven.agent.http` with embedded Tomcat `HttpTestStack` fixture
- [x] Implement `HttpTestStack` inner class: start embedded Tomcat on port 0, register AuthFilter + RateLimitFilter + HttpApiServlet on `/api/v1/*`, configure with test API key and low rate-limit threshold
- [x] Implement JSON response parsing helper: extract status code, headers, and typed fields from `HttpResponse<String>`
- [x] Implement health endpoint tests: GET /api/v1/health returns 200 with `status=ok` and `version=0.1.0`, no auth required
- [x] Implement tools list tests: GET /api/v1/tools with valid auth returns 200 with tools array containing registered tool metadata
- [x] Implement agent run tests: POST /api/v1/agent/run with valid auth and prompt returns 200 with agentId, output, and state=STOPPED; missing prompt returns 400; empty body returns 400
- [x] Implement agent state tests: GET /api/v1/agent/state with valid id returns 200 with agentId, state, timestamps; unknown id returns 404; missing id returns 400
- [x] Implement agent stop tests: POST /api/v1/agent/stop with valid id returns 200; unknown id returns 404; missing id returns 400
- [x] Implement auth filter tests: valid Bearer token passes; valid X-API-Key passes; missing auth returns 401; invalid key returns 401; health endpoint bypasses auth
- [x] Implement rate-limit tests: requests under limit pass; requests over limit return 429 with Retry-After header; window expiry allows new requests
- [x] Implement error path tests: unknown route returns 404; wrong HTTP method returns 405
- [x] Implement agent lifecycle test: POST /agent/run → GET /agent/state → POST /agent/stop → GET /agent/state, verifying state transitions across the full sequence
- [x] Write delta spec file `http-e2e-tests.md` under `changes/http-e2e-tests/specs/`

## Testing

- [x] Lint: run `mvn compile` to verify no compilation errors
- [x] Unit test: run `mvn test -pl . -Dtest="org.specdriven.agent.http.HttpE2eTest"` to execute e2e tests
- [x] Regression: run `mvn test` to verify all existing tests still pass

## Verification

- [x] Verify all e2e test scenarios correspond to requirements in `http-api.md`
- [x] Verify no production code was modified (test-only change)
- [x] Verify delta spec accurately describes the tested scenarios

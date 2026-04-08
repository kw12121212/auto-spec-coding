# Design: http-e2e-tests

## Approach

Create a `HttpE2eTest` class in `org.specdriven.agent.http` following the `TestStack` pattern established by `JsonRpcEndToEndTest`. The test stack will:

1. Start an embedded `org.apache.catalina.startup.Tomcat` instance on a random available port. Tomcat is already a transitive dependency through `lealone-http` (which packages `TomcatServer`).
2. Register `AuthFilter` and `RateLimitFilter` on `/api/v1/*` via `FilterRegistration.Dynamic`, and `HttpApiServlet` on `/api/v1/*` via `ServletRegistration.Dynamic`.
3. Configure auth filter with a known test API key (`test-api-key`) via init parameters.
4. Configure rate-limit filter with a low threshold (`max=5`, `windowSeconds=60`) for testability.
5. Construct a `SpecDriven` SDK with a `StubTool` (matching the pattern from `HttpApiServletTest`) and inject it into `HttpApiServlet` via constructor.
6. Use Java 11+ `java.net.http.HttpClient` to send real HTTP requests and parse JSON responses.

Each test method sends real HTTP requests and asserts on status code, response headers, and JSON body fields.

## Key Decisions

- **Embedded Tomcat over mock servlet environment**: Existing unit tests already use `StubRequest`/`StubResponse` to test servlet logic in isolation. E2E tests add value by exercising real HTTP serialization, filter chain ordering, and servlet container behavior — which requires a real server.
- **Java HttpClient over external HTTP client library**: `java.net.http.HttpClient` is built into the JDK (11+) and requires no additional dependency, consistent with the project's minimal-dependency philosophy.
- **Random port binding**: Use port `0` to let the OS assign an available port, avoiding conflicts with local services. Retrieve the actual port from `Tomcat.getConnector().getLocalPort()` after startup.
- **Shared test stack per test class**: Use JUnit 5 `@BeforeAll`/`@AfterAll` lifecycle to start/stop the server once per test class, not per test method. Reset rate-limit counters between tests via a fresh `RateLimitFilter` instance or by waiting for window expiry.
- **StubTool reuse**: Reuse the `StubTool` pattern from existing unit tests — a minimal `Tool` implementation that returns a fixed result without real side effects.

## Alternatives Considered

- **WireMock / MockWebServer**: Would avoid needing a real servlet container, but adds an external dependency and doesn't test the actual filter chain. Rejected — the goal is to validate real HTTP integration.
- **Lealone TomcatServer directly**: Could use `TomcatServer` from `lealone-http`, but it loads its own configuration. Embedding Tomcat directly gives full control over filter/servlet registration for test purposes.
- **Separate test module**: Could isolate e2e tests in a Maven profile or module. Rejected for now — the JSON-RPC e2e tests live alongside unit tests in the same module, so follow the same convention.

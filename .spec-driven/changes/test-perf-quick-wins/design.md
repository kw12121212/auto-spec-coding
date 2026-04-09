# Design: test-perf-quick-wins

## Approach

Four independent optimizations, each touching only test code or build configuration:

### 1. Python mock server reuse (MCP/LSP tests)

Current: Each `@Test` method writes a Python script to a temp file and spawns a new `python3` process. Python startup alone costs ~1.5s per invocation, and 8 tests across 4 files means ~12 process spawns.

Approach:
- Move mock server script creation and process launch to `@BeforeAll` static methods
- Each test class creates ONE Python process that handles multiple request sequences
- Mock Python servers must be stateless between requests (already true for the current protocol handler pattern)
- `@AfterAll` sends shutdown and closes the process
- Tests that need different server behaviors (e.g., error server, slow server, bad version server) still need separate processes — but each variant is started once, not per-test

Files affected:
- `src/test/java/org/specdriven/agent/mcp/McpClientTest.java`
- `src/test/java/org/specdriven/agent/mcp/McpClientRegistryTest.java`
- `src/test/java/org/specdriven/agent/tool/LspClientTest.java`
- `src/test/java/org/specdriven/agent/tool/LspToolTest.java`

### 2. Thread.sleep elimination (LealoneCronStoreTest)

Current: `Thread.sleep(4000)` and `Thread.sleep(3000)` to wait for the background scheduler (1s poll interval) to fire one-shot entries.

Approach:
- Replace the `AtomicInteger fireCount` callback with a `CountDownLatch` or `CompletableFuture` in the test's callback
- Tests await on the latch with a generous timeout (e.g., 5s) instead of fixed sleep
- The latch fires immediately when the scheduler processes the entry, eliminating wasted wait time
- Lealone DB creation per-test is kept as-is (acceptable for this scope)

File affected:
- `src/test/java/org/specdriven/agent/registry/LealoneCronStoreTest.java`

### 3. HTTP server reuse (OpenAI/Claude client tests)

Current: `@BeforeEach` creates and starts a new `HttpServer` with random port; `@AfterEach` stops it. 12 tests × ~0.5s startup each.

Approach:
- Convert to `@BeforeAll`/`@AfterAll` static lifecycle
- Server uses random port (`InetSocketAddress(0)`) so no port conflict risk between parallel test classes
- Each test overrides the server's context handler before making requests (already the pattern in most tests via `server.createContext()`)
- The handler override is safe because tests run sequentially within a class

Files affected:
- `src/test/java/org/specdriven/agent/agent/OpenAiClientTest.java`
- `src/test/java/org/specdriven/agent/agent/ClaudeClientTest.java`

### 4. Surefire parallel execution

Current: All tests run sequentially (default surefire behavior).

Approach:
- Add `<parallel>methods</parallel>` and `<useUnlimitedThreads>true</useUnlimitedThreads>` to maven-surefire-plugin configuration in pom.xml
- Tests within a class run in parallel; classes also run in parallel
- This is safe because:
  - Lealone store tests use unique DB names per test (UUID-based)
  - HTTP servers use random ports (`InetSocketAddress(0)`)
  - Python processes communicate via stdin/stdout (no port binding)
  - No shared mutable static state observed in test classes
- Configure `<threadCount>4</threadCount>` as a reasonable default to avoid resource contention

File affected:
- `pom.xml` (maven-surefire-plugin configuration section)

## Key Decisions

- **Reuse Python processes rather than replace with Java mocks**: Maintains end-to-end JSON-RPC protocol validation; less risky than rewriting test infrastructure
- **Keep Lealone DB per-test initialization**: Medium savings (~5-10s) but higher risk of test interference; out of scope for this change
- **Use Surefire parallel rather than JUnit parallel**: Surefire config is simpler (single pom.xml change) vs adding `@Execution(CONCURRENT)` annotations across 112 test files
- **CountDownLatch over CompletableFuture for cron tests**: Simpler API for the single-event-wait pattern; CompletableFuture adds unnecessary complexity

## Alternatives Considered

- **Replace Python mocks with Java in-process mocks**: Would eliminate subprocess overhead entirely but loses protocol-level validation. Higher implementation effort and risk.
- **Shared Lealone DB across test classes via @BeforeAll + TRUNCATE**: Could save ~5-10s but risks test data leaking between classes; deferred.
- **JUnit 5 `@Execution(CONCURRENT)` per-class annotations**: More granular control but requires touching every test file; Surefire config is sufficient.
- **Testcontainers for isolated DB**: Overkill for an embedded database; adds Docker dependency.

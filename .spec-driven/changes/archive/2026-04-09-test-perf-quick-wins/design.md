# Design: test-perf-quick-wins

## Approach

Five scoped changes: four test/build optimizations plus one user-approved repository fix required to remove an unrelated pre-existing suite blocker.

### 1. Replace Python MCP/LSP mocks with Java

Current: MCP/LSP tests write Python scripts to temp files and launch them with `python3`. This adds an external runtime dependency and makes the test setup cost dominated by subprocess startup and script generation.

Approach:
- Replace embedded Python scripts with Java test-only mock server launchers
- Launch those mocks via the current JVM's `java` binary and the test classpath, so the tests depend only on Java
- Keep the existing subprocess boundary and JSON-RPC/LSP framing behavior, preserving end-to-end protocol validation
- Reuse mock server processes where it is safe and simple, but prefer isolation over aggressive sharing if it avoids parallel-test interference
- Represent the current MCP variants (standard, bad-version, error, slow) and LSP variants (standard, slow) as Java launch modes instead of separate Python scripts

Files affected:
- `src/test/java/org/specdriven/agent/mcp/McpClientTest.java`
- `src/test/java/org/specdriven/agent/mcp/McpClientRegistryTest.java`
- `src/test/java/org/specdriven/agent/tool/LspClientTest.java`
- `src/test/java/org/specdriven/agent/tool/LspToolTest.java`
- New Java test helper classes under `src/test/java/...` for MCP/LSP mock server launchers

### 2. Thread.sleep elimination (LealoneCronStoreTest)

Current: `Thread.sleep(4000)` and `Thread.sleep(3000)` wait a fixed amount of time for the background scheduler (1s poll interval) to fire one-shot entries.

Approach:
- Replace the `AtomicInteger fireCount` callback with a `CountDownLatch` or `CompletableFuture` in the test's callback
- Tests await on the latch with a generous timeout (e.g., 5s) instead of fixed sleep
- The latch fires immediately when the scheduler processes the entry, eliminating wasted wait time
- Lealone DB creation per-test is kept as-is (acceptable for this scope)

File affected:
- `src/test/java/org/specdriven/agent/registry/LealoneCronStoreTest.java`

### 3. HTTP server reuse (OpenAI/Claude client tests)

Current: `@BeforeEach` creates and starts a new `HttpServer` with random port; `@AfterEach` stops it. 12 tests pay repeated server startup cost.

Approach:
- Convert to `@BeforeAll`/`@AfterAll` static lifecycle
- Server uses random port (`InetSocketAddress(0)`) so no port conflict risk between parallel test classes
- Replace per-test `createContext()` calls with one stable context whose handler delegates through an atomic reference, avoiding duplicate-context errors when tests run in parallel
- Reset the handler between tests so assertions remain isolated

Files affected:
- `src/test/java/org/specdriven/agent/agent/OpenAiClientTest.java`
- `src/test/java/org/specdriven/agent/agent/ClaudeClientTest.java`

### 4. Surefire parallel execution

Current: All tests run sequentially (default surefire behavior).

Approach:
- Add Maven/JUnit parallel test configuration in `pom.xml`
- Prefer class-level parallelism by default so independent test classes run concurrently without forcing method-level concurrency inside stateful test classes
- Keep test fixtures isolated where shared class-level infrastructure is introduced
- Ensure the updated MCP/LSP and HTTP tests remain safe under the selected parallel mode

File affected:
- `pom.xml` (maven-surefire-plugin configuration section)

### 5. Restore missing bundled `rg` resource

Current: `DefaultBuiltinToolManagerTest.classpathResourceExistsForCurrentPlatform` fails because the repository does not ship `src/main/resources/builtin-tools/linux-x86_64/rg` on this platform, even though the main builtin-tool spec already requires that classpath resource contract.

Approach:
- Add the current platform's bundled `rg` resource under `src/main/resources/builtin-tools/`
- Keep the fix minimal: do not refactor `DefaultBuiltinToolManager`, only restore the missing packaged artifact that its existing logic already expects
- Preserve the existing `resolve()` / `detect()` behavior and remove the unrelated suite blocker by making the resource actually present on the classpath

Files affected:
- `src/main/resources/builtin-tools/linux-x86_64/rg`
- `.spec-driven/changes/test-perf-quick-wins/specs/builtin-tool-manager.md`

## Key Decisions

- **Replace Python mocks with Java subprocess mocks**: Removes the external Python dependency while still preserving real subprocess and protocol-boundary coverage
- **Keep Lealone DB per-test initialization**: Medium savings (~5-10s) but higher risk of test interference; out of scope for this change
- **Use build-level parallel configuration rather than touching many individual test classes**: Keep the concurrency change centralized in the build/test configuration
- **CountDownLatch over CompletableFuture for cron tests**: Simpler API for the single-event-wait pattern; CompletableFuture adds unnecessary complexity
- **Restore the missing resource instead of weakening the test**: The failing builtin-tool test reflects an existing main-spec requirement, so the correct fix is to ship the resource rather than relax the assertion

## Alternatives Considered

- **Replace Python mocks with Java in-process mocks**: Would eliminate subprocess overhead entirely but loses protocol-level validation. Higher implementation effort and risk.
- **Keep Python but share processes across tests**: Improves speed, but violates the new requirement that tests must not depend on an external Python runtime.
- **Shared Lealone DB across test classes via @BeforeAll + TRUNCATE**: Could save ~5-10s but risks test data leaking between classes; deferred.
- **JUnit 5 `@Execution(CONCURRENT)` per-class annotations**: More granular control but requires touching every test file; Surefire config is sufficient.
- **Testcontainers for isolated DB**: Overkill for an embedded database; adds Docker dependency.

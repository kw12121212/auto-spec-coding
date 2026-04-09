# Tasks: test-perf-quick-wins

## Implementation

- [ ] McpClientTest: Convert to @BeforeAll/@AfterAll Python mock server lifecycle — extract mock server variants (standard, bad-version, error, slow) to static fields initialized once; share across tests that use the same variant
- [ ] McpClientRegistryTest: Convert to @BeforeAll/@AfterAll Python mock server lifecycle — start one shared mock server for all registry tests
- [ ] LspClientTest: Convert to @BeforeAll/@AfterAll Python mock server lifecycle — start one shared mock LSP server
- [ ] LspToolTest: Convert to @BeforeAll/@AfterAll Python mock server lifecycle — start one shared mock LSP server
- [ ] LealoneCronStoreTest: Replace Thread.sleep with CountDownLatch — use CountDownLatch in the fire callback; await with timeout instead of fixed sleep in `oneShot_firesAfterDelay` and `oneShot_publishesCronTriggered`
- [ ] OpenAiClientTest: Convert HTTP server from @BeforeEach to @BeforeAll static lifecycle — move server creation/start to static @BeforeAll; keep per-test context handler overrides
- [ ] ClaudeClientTest: Convert HTTP server from @BeforeEach to @BeforeAll static lifecycle — same pattern as OpenAiClientTest
- [ ] pom.xml: Add Surefire parallel execution config — `<parallel>both</parallel>`, `<useUnlimitedThreads>true</useUnlimitedThreads>` in maven-surefire-plugin (no fixed threadCount, use all CPU cores)

## Testing

- [ ] Validation: run `mvn test-compile` to verify all test classes compile after refactoring
- [ ] Unit tests: run `mvn test` and verify all 980 tests pass with zero failures and zero errors
- [ ] Performance: run `mvn test` and verify total time is under 35 seconds (baseline: 79s)
- [ ] Stability: run `mvn test` three times consecutively to confirm no flaky failures from parallel execution

## Verification

- [ ] Verify all test assertions remain unchanged from original
- [ ] Verify no production code was modified
- [ ] Verify test count remains 980 (no tests removed or skipped)

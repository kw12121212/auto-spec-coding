# Tasks: test-perf-quick-wins

## Implementation

- [x] Add Java test-only MCP mock launcher(s) that emulate the current standard, bad-version, error, and slow server behaviors without any Python dependency
- [x] Add Java test-only LSP mock launcher(s) that emulate the current standard and slow server behaviors without any Python dependency
- [x] McpClientTest: replace Python script generation and `python3` command usage with Java mock launcher commands while preserving existing assertions
- [x] McpClientRegistryTest: replace Python script generation and YAML `python3` command usage with Java mock launcher commands while preserving existing assertions
- [x] LspClientTest: replace Python script generation and `python3` command usage with Java mock launcher commands while preserving existing assertions
- [x] LspToolTest: replace Python script generation and `python3` command usage with Java mock launcher commands while preserving existing assertions
- [x] LealoneCronStoreTest: Replace Thread.sleep with CountDownLatch — use CountDownLatch in the fire callback; await with timeout instead of fixed sleep in `oneShot_firesAfterDelay` and `oneShot_publishesCronTriggered`
- [x] OpenAiClientTest: Convert HTTP server from @BeforeEach to @BeforeAll static lifecycle — move server creation/start to static @BeforeAll; keep per-test context handler overrides
- [x] ClaudeClientTest: Convert HTTP server from @BeforeEach to @BeforeAll static lifecycle — same pattern as OpenAiClientTest
- [x] pom.xml: Add test parallel execution config that safely speeds up the suite without requiring method-level concurrency in stateful test classes
- [x] Restore the current platform bundled `rg` resource under `src/main/resources/builtin-tools/` so `DefaultBuiltinToolManager` satisfies the existing classpath resource contract

## Testing

- [x] Validation: run `mvn test-compile` to verify all test classes compile after refactoring
- [x] Unit tests: run `mvn test` and verify all 980 tests pass with zero failures and zero errors
- [x] Performance: run `mvn test` and verify total time is under 35 seconds (baseline: 79s)
- [x] Stability: run `mvn test` three times consecutively to confirm no flaky failures from parallel execution
- [x] Validation: run `DefaultBuiltinToolManagerTest` to verify the restored bundled resource is visible on the classpath

## Verification

- [x] Verify all test assertions remain unchanged from original
- [x] Verify no production Java source was modified outside the approved bundled resource restoration
- [x] Verify MCP/LSP tests no longer require external Python on the PATH
- [x] Verify test count remains 980 (no tests removed or skipped)

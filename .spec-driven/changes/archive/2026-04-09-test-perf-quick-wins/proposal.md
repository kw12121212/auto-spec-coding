# Proposal: test-perf-quick-wins

## What

Optimize test execution speed from ~79s to ~30s by addressing the four highest-impact bottlenecks: removing Python-based MCP/LSP test dependencies in favor of Java mock servers, eliminating `Thread.sleep` in cron tests, optimizing HTTP server lifecycle in LLM client tests, and enabling Maven test parallelism. After the user approved a small scope expansion, this change also restores the missing bundled `rg` resource for the current platform so the full suite can return to green.

## Why

The 980-test suite takes 79 seconds, with ~70s concentrated in 15 test classes. The dominant costs are:
- **~35s** wasted in MCP/LSP test setup because tests depend on external Python mock servers that are recreated for each test case
- **~10s** spent in Thread.sleep hard waits (CronStore tests sleep 3-7 seconds waiting for scheduler polling)
- **~8s** on HTTP server create/destroy per test (OpenAI/Claude clients use @BeforeEach instead of @BeforeAll)
- Remaining time lost to sequential execution of independent tests

Developers run tests frequently; this friction compounds across the team. Requiring Python for the MCP/LSP tests also adds an avoidable external dependency to local and CI environments.

## Scope

1. **Replace Python test dependencies with Java**: Convert McpClientTest, LspClientTest, McpClientRegistryTest, and LspToolTest to use Java-based mock servers/launchers instead of Python scripts, so the tests require no external Python runtime
2. **Thread.sleep elimination**: Replace Thread.sleep in LealoneCronStoreTest with CountDownLatch or polling await mechanism
3. **HTTP server reuse**: Convert OpenAiClientTest and ClaudeClientTest mock HTTP server from @BeforeEach to @BeforeAll static lifecycle
4. **Surefire parallel execution**: Configure maven-surefire-plugin with parallel methods execution for independent tests
5. **Restore missing bundled tool resource**: Add the current platform's bundled `rg` resource under `src/main/resources/builtin-tools/` so `DefaultBuiltinToolManager` can satisfy its existing classpath resource contract and the unrelated suite failure is removed

## Unchanged Behavior

- All 980 tests must continue to pass with identical assertions
- Test coverage must not decrease
- No production Java source behavior changes beyond the approved bundled resource restoration
- MCP/LSP test behavior and protocol assertions must remain the same after replacing Python mocks with Java mocks
- No changes to observable component behavior described in specs

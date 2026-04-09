# Proposal: test-perf-quick-wins

## What

Optimize test execution speed from ~79s to ~30s by addressing the four highest-impact bottlenecks: Python subprocess reuse in MCP/LSP tests, Thread.sleep elimination in cron tests, HTTP server lifecycle optimization in LLM client tests, and Maven Surefire parallel execution.

## Why

The 980-test suite takes 79 seconds, with ~70s concentrated in 15 test classes. The dominant costs are:
- **~35s** wasted on repeated Python subprocess spawning (MCP/LSP tests create a new Python mock server per @Test instead of sharing via @BeforeAll)
- **~10s** spent in Thread.sleep hard waits (CronStore tests sleep 3-7 seconds waiting for scheduler polling)
- **~8s** on HTTP server create/destroy per test (OpenAI/Claude clients use @BeforeEach instead of @BeforeAll)
- Remaining time lost to sequential execution of independent tests

Developers run tests frequently; this friction compounds across the team.

## Scope

1. **Python mock server reuse**: Convert McpClientTest, LspClientTest, McpClientRegistryTest, LspToolTest from per-test to per-class Python process lifecycle (@BeforeAll/@AfterAll)
2. **Thread.sleep elimination**: Replace Thread.sleep in LealoneCronStoreTest with CountDownLatch or polling await mechanism
3. **HTTP server reuse**: Convert OpenAiClientTest and ClaudeClientTest mock HTTP server from @BeforeEach to @BeforeAll static lifecycle
4. **Surefire parallel execution**: Configure maven-surefire-plugin with parallel methods execution for independent tests

## Unchanged Behavior

- All 980 tests must continue to pass with identical assertions
- Test coverage must not decrease
- No business/production code changes
- No changes to observable component behavior described in specs

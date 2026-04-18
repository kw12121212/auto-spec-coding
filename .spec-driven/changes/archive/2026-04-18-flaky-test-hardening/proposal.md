# flaky-test-hardening

## What

Fix root causes of flaky tests across the test suite: replace timing-based
`Thread.sleep` waits with deterministic synchronization, eliminate shared
mutable state between tests, and isolate non-deterministic inputs so that test
outcomes are repeatable regardless of host load or execution order.

## Why

M39 expanded regression coverage for high-risk behavior surfaces. That coverage
is only trustworthy if the tests themselves are deterministic. The suite
currently contains 79 `Thread.sleep` calls across 20+ test files and 39 shared
static fields — the primary source of race conditions and order-dependent
failures. Flaky failures erode developer trust in the regression signal and
block confident progress toward M40's test-infrastructure quality gates.

## Scope

**In scope:**
- Replace unconditional `Thread.sleep` waits in tests with deterministic
  alternatives: `CountDownLatch`, `CompletableFuture.get(timeout)`, polling with
  bounded retries and explicit timeout, or Awaitility-style await helpers
- Eliminate shared mutable static state between test methods; use per-test
  setup/teardown (`@BeforeEach`/`@AfterEach`) for state that must be fresh
- Isolate port allocation in tests that start embedded servers to avoid
  cross-test port conflicts
- Scope limited to `src/test/` — no production code changes

**Out of scope:**
- `Thread.sleep` calls that are semantically required (e.g., `MockMcpServerMain`
  long sleep that simulates server lifetime)
- New business regression tests (M39 scope)
- Test fixture extraction or standardization (M40 `test-fixture-standardization`)
- Quality gate command standardization (M40 `test-command-quality-gates`)

## Unchanged Behavior

- All tests that currently pass MUST continue to pass after this change
- No external-facing API or SDK behavior is changed
- Test assertions must remain equivalent — only the wait/synchronization
  mechanism changes, not what is being asserted

# Tasks: flaky-test-hardening

## Implementation

- [x] Audit all 79 `Thread.sleep` callsites in `src/test/` and categorize each
      as: (a) event-wait race, (b) server-startup race, (c) rate-limit clock-advance,
      (d) intentional/safe — document categorization inline as a comment
- [x] Replace event-wait `Thread.sleep` sleeps in `DefaultOrchestratorTest`,
      `JsonRpcDispatcherTest`, `JsonRpcEndToEndTest`, `WorkflowRuntimeTest`,
      `WorkflowStepCompositionTest`, `WorkflowAgentHumanBridgeTest`, and
      `SdkAgentEventTest` with `CountDownLatch` or `CompletableFuture.get(timeout)`
- [x] Replace server-startup `Thread.sleep` in `ServerToolLifecycleTest` and
      `HttpE2eTest` with bounded TCP-probe polling (reuse or extract the
      `waitForPort` pattern already present in `HttpE2eTest`)
- [x] Replace `HttpE2eTest` rate-limit wall-clock sleep
      (`Thread.sleep((WINDOW_SEC + 1) * 1000L)`) with a `Clock`-injectable
      rate-limiter; inject a controllable clock in the test
- [x] Fix `LealonePlatformTest` timing sleeps using event or future-based
      synchronization on the async operation being awaited
- [x] Audit shared static mutable fields: for each field identified as
      mutated across test methods, migrate to `@BeforeEach` / `@AfterEach` lifecycle

## Testing

- [x] Run lint and validate: `mvnd checkstyle:check` — must produce zero violations
- [x] Run unit tests: `mvnd test` — all tests must pass with zero failures and zero errors
- [x] Run `mvnd test` a second consecutive time — both runs must produce identical
      pass/fail results (confirms no order-dependent flakiness)

## Verification

- [x] Confirm no `Thread.sleep` callsite categorized as (a), (b), or (c) remains
      in `src/test/` after implementation
- [x] Confirm no test class retains shared mutable static state that is written
      during a test method
- [x] Confirm the rate-limit test in `HttpE2eTest` completes in under 5 seconds
      wall-clock time (previously required `(WINDOW_SEC + 1)` seconds of sleep)
- [x] Verify implementation matches proposal scope — no production code changed
      except the `Clock` injection refactor permitted by design.md

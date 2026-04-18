# Design: flaky-test-hardening

## Approach

1. **Audit pass first**: run `mvnd test` multiple times (or inspect each
   `Thread.sleep` callsite) to confirm which sleeps are load-sensitive races vs.
   intentional pauses. Categorize each callsite as: (a) event-wait race, (b)
   server-startup race, (c) rate-limit clock advance, (d) intentional/safe.

2. **Replace event-wait races** with `CountDownLatch` or `CompletableFuture`:
   the code under test already emits events or returns futures in many cases
   (49 existing `CountDownLatch`/`CompletableFuture` usages confirm the pattern
   is available). Register a listener before the action, then `await(timeout)`.

3. **Replace server-startup races** with TCP-probe polling: `ServerToolLifecycleTest`
   and `HttpE2eTest` start embedded servers and then sleep. Replace with a
   bounded retry loop that probes the port (pattern already present in
   `HttpE2eTest.waitForPort`) rather than sleeping a fixed interval.

4. **Replace rate-limit clock-advance sleeps** in `HttpE2eTest` with a
   deterministic clock abstraction: inject a `Clock` or advance a fake time
   source rather than sleeping `(WINDOW_SEC + 1) * 1000L` milliseconds in the
   test process.

5. **Eliminate shared static state**: for each static field that is mutated
   across tests, move initialization into `@BeforeEach` and teardown into
   `@AfterEach`. Where the field is final and truly immutable, leave it.

## Key Decisions

- **No Awaitility dependency**: the existing `CountDownLatch` / `CompletableFuture`
  patterns cover the async-wait cases without adding an external library.
  Awaitility is considered only if a polling pattern cannot be expressed cleanly
  with JDK primitives.

- **Safe `Thread.sleep` callsites are left untouched**: `MockMcpServerMain:47`
  uses `Thread.sleep(60_000)` to hold a server alive — that is intentional and
  not a race. Only callsites where the sleep is a proxy for "wait until some
  async event occurred" are replaced.

- **Clock injection for rate-limit tests**: sleeping `(WINDOW_SEC + 1) * 1000L`
  milliseconds is a multi-second wall-clock delay that makes the test suite
  slow and load-sensitive. A clock abstraction is the correct fix; if the
  production code does not yet accept a `Clock`, a small refactor scoped to that
  one class is acceptable within this change.

## Alternatives Considered

- **Increase sleep durations**: rejected — longer sleeps make CI slower and
  still fail under extreme load; they treat the symptom, not the cause.
- **Retry-on-failure annotations**: rejected — retries hide root causes and
  produce false positives in the regression signal.
- **Awaitility**: acceptable fallback if JDK primitives are insufficient, but
  adds an external dependency; prefer JDK-native patterns first.

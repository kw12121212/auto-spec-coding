# Questions: test-perf-quick-wins

## Open

## Resolved

- [x] Q: Should MCP/LSP test mocks continue to use external Python subprocesses?
  Context: The original proposal optimized Python mock lifecycle, but the updated change requirement is that tests must not depend on external Python.
  A: No. Replace all Python-based MCP/LSP test dependencies with Java-based test helpers or launchers so the test suite requires only Java.

- [x] Q: Thread count for Surefire parallel execution — 4 threads is a conservative default. Should this match the machine's CPU core count, or stay at 4 for CI reproducibility?
  Context: Higher thread counts reduce time on multi-core machines but may cause resource contention in CI environments
  A: Use CPU core count (`useUnlimitedThreads=true`) for maximum local dev speed

- [x] Q: Should this change expand scope to fix the unrelated builtin-tool test blocker after the performance work is complete?
  Context: The suite remained red only because the repository is missing the current platform bundled `rg` resource expected by `DefaultBuiltinToolManagerTest`.
  A: Yes. Add the minimal bundled resource restoration needed to bring the full suite back to green.

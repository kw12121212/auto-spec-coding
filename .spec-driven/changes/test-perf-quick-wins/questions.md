# Questions: test-perf-quick-wins

## Open

## Resolved

- [x] Q: Thread count for Surefire parallel execution — 4 threads is a conservative default. Should this match the machine's CPU core count, or stay at 4 for CI reproducibility?
  Context: Higher thread counts reduce time on multi-core machines but may cause resource contention in CI environments
  A: Use CPU core count (`useUnlimitedThreads=true`) for maximum local dev speed

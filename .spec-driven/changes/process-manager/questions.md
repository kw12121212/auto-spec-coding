# Questions: process-manager

## Open

<!-- No open questions -->

## Resolved

- [x] Q: What JDK version should ProcessManager target?
  Context: M15 notes mention "JDK 25 ProcessHandle API" — needed to determine available API surface.
  A: JDK 25

- [x] Q: Should the output buffer have a maximum size limit?
  Context: M15 risks mention long-running processes may exhaust memory via unbounded output.
  A: Yes — configurable per-process limit (default 1MB), tail-truncation on overflow (keeps most recent output).

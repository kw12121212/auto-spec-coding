# Questions: flaky-test-hardening

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Which flaky tests or categories should be prioritized?
  Context: 206 test files — needed to scope tasks.md to real candidates.
  A: Systematic audit pass first. Categorize all `Thread.sleep` callsites, then
     fix each category in order: event-wait races, server-startup races,
     rate-limit clock-advance sleeps. Safe sleeps (e.g. MockMcpServerMain) left
     untouched. (Accepted by user: 接受所有建议, 2026-04-17)

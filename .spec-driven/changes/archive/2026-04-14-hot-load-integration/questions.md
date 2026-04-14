# Questions: hot-load-integration

## Open

<!-- Add open questions here using the format below -->
<!-- - [ ] Q: <question text> -->
<!--   Context: <why this matters / what depends on the answer> -->

## Resolved

<!-- Resolved questions are moved here with their answers -->
<!-- - [x] Q: <question text> -->
<!--   Context: <why this matters> -->
<!--   A: <answer from human> -->
- [x] Q: Should `DiscoveryResult.errors` include hot-load failures, or remain SQL-only?
  Context: The proposal scope said hot-load failures should be appended to `errors`,
  while the unchanged-behavior section said `errors` should remain SQL-only.
  A: Keep `registeredCount` and `failedCount` SQL-only, add hot-load counters, and let
  `errors` include both SQL and hot-load failures.

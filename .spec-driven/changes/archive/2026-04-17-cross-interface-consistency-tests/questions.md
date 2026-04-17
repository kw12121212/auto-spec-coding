# Questions: cross-interface-consistency-tests

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should the testing phase use `mvn` when `mvnd` is unavailable in the execution environment?
  Context: The M39 milestone notes and this change's testing tasks standardize on `mvnd`, but this environment does not have `mvnd` installed and provides only `mvn`.
  A: Yes. Use `mvn` as the fallback for this run.

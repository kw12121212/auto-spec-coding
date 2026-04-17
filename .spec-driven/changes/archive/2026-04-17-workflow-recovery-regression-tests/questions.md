# Questions: workflow-recovery-regression-tests

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should the testing phase use `mvn` when `mvnd` is unavailable in the execution environment?
  Context: The milestone and proposal standardized on `mvnd`, but this environment does not have `mvnd` installed and provides no repository-local wrapper.
  A: Yes. Use the equivalent `mvn` commands as an explicit fallback for this auto run.
- [x] Q: Should this change expand enough to fix an unrelated full-suite test failure discovered during the required verification gate?
  Context: The workflow recovery tests passed, but the required repository-wide unit suite failed in an unrelated integration test outside the original narrow scope.
  A: Yes. Expand the change only as much as needed to fix the blocking full-suite failure and continue the auto workflow.

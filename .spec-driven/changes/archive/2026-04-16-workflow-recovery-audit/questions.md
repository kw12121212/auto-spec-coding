# Questions: workflow-recovery-audit

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should the first `workflow-recovery-audit` proposal use a runtime-driven or caller-driven recovery model?
  Context: This decides whether the proposal should add a new public recovery control surface or keep the first change focused on runtime-managed checkpoints, retry, and audit behavior.
  A: Use the runtime-driven model. The first proposal should rely on runtime-managed checkpoint recovery and supported automatic retry without adding a new caller-triggered recovery API. (User answer: accepted the recommendation.)

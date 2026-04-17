# workflow-recovery-regression-tests

## What

Strengthen automated regression coverage for the existing workflow runtime recovery
surface, focusing on pause/resume, checkpoint recovery, retry exhaustion, and
workflow audit events that are already defined in the current specs.

This change will add or expand tests around the supported workflow lifecycle and
recovery contracts without introducing new workflow features or broad test
infrastructure refactoring.

Because this change's required full-suite verification gate is currently blocked
by an unrelated repository test-harness failure, the change may also include
the smallest repository-local test fix needed to restore that gate, provided it
does not change product behavior.

## Why

The current roadmap phase prioritizes high-risk regression confidence before any
test-infrastructure cleanup. The workflow runtime is one of the most stateful
surfaces in the repository: it combines asynchronous progression,
waiting-for-input pauses, answer-driven resume, checkpoint continuity after
runtime reconstruction, retry handling, and auditable lifecycle events.

M39 explicitly calls out workflow pause/resume, checkpoint recovery, retry
exhaustion, and audit-event regression coverage as a planned next step. Adding
that protection now reduces the risk of state-machine regressions while staying
inside already-defined observable contracts.

During execution, the repository-wide test gate also exposed an unrelated
integration-test startup failure. Restoring that gate is necessary to finish the
proposal's required testing task and keep repository verification trustworthy.

## Scope

In scope:
- Expand regression tests for workflow pause and resume behavior already defined
  in `workflow/workflow-runtime.md`.
- Expand regression tests for checkpoint recovery and workflow identity
  continuity after runtime reconstruction.
- Expand regression tests for retryable step failures, retry exhaustion, and
  diagnosable final failure views.
- Expand regression tests for observable workflow audit events related to pause,
  resume, checkpoint save, recovery, and retry scheduling.
- Make only the smallest local test-fixture or assertion-helper changes needed
  to support these regression cases.
- If the required full-suite verification is blocked by an existing unrelated
  repository test-harness failure, apply only the smallest fix needed to make
  that gate runnable again without changing product behavior.

Out of scope:
- Any new workflow runtime capability, recovery policy, retry policy, or audit
  event shape.
- Cross-interface consistency testing across SDK, HTTP, and JSON-RPC.
- Repository-wide fixture standardization, flaky-test cleanup, or quality-gate
  redesign from M40.
- Refactoring production workflow code unless a minimal test-support adjustment
  is strictly required by the existing observable contract.
- Expanding the change into new multi-layer feature work beyond the minimal fix
  required to unblock the committed full-suite test gate.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- The existing workflow runtime public behavior remains defined by the current
  workflow specs.
- Existing SDK, HTTP API, and JSON-RPC behavior outside workflow recovery tests
  remains unchanged.
- Test infrastructure conventions remain unchanged except for minimal local
  support needed by the added regression cases.

# Tasks: workflow-recovery-regression-tests

## Implementation

- [x] Review current workflow runtime, workflow step composition, human-bridge,
  and event-system tests against the existing recovery-related specs.
- [x] Extend workflow regression tests to cover pause/resume behavior,
  question-to-workflow correlation, and resumed execution continuity where
  coverage is currently missing.
- [x] Extend workflow regression tests to cover checkpoint recovery,
  first-incomplete-step resume behavior, retry scheduling, retry exhaustion, and
  diagnosable failure views where coverage is currently missing.
- [x] Make only the smallest local test-fixture or assertion-helper changes
  needed to support the added workflow recovery regression cases.
- [x] Diagnose and fix the smallest unrelated repository test-harness failure
  required to make the full-suite verification task pass.

## Testing

- [x] Run validation build `mvnd compile -q`.
- [x] Run targeted unit tests `mvnd -q -Dtest=WorkflowRuntimeTest,WorkflowStepCompositionTest,WorkflowAgentHumanBridgeTest,EventSystemTest test -Dsurefire.useFile=false`.
- [x] Run full unit test suite `mvnd test -q -Dsurefire.useFile=false`.

## Verification

- [x] Verify the added tests map only to already-specified observable behavior
  in `workflow/workflow-runtime.md` and `workflow/workflow-step-composition.md`.
- [x] Verify the change does not broaden scope beyond workflow recovery
  regression coverage plus the minimal full-suite blocker fix.

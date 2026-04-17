# Design: workflow-recovery-regression-tests

## Approach

Add regression coverage directly around the current observable contracts already
defined in the main specs:
- `workflow/workflow-runtime.md`
- `workflow/workflow-step-composition.md`

The implementation should prefer extending the existing focused workflow test
classes instead of introducing a new shared test framework layer. Expected test
touch points are:
- `src/test/java/org/specdriven/sdk/WorkflowRuntimeTest.java`
- `src/test/java/org/specdriven/sdk/WorkflowStepCompositionTest.java`
- `src/test/java/org/specdriven/sdk/WorkflowAgentHumanBridgeTest.java`
- `src/test/java/org/specdriven/agent/event/EventSystemTest.java`

If a small supporting helper adjustment is required to keep tests stable and
readable, keep it local to the workflow regression surface and avoid turning
this change into general test-infrastructure work.

## Key Decisions

- Treat this as a test-only change with no observable spec delta.
  Rationale: the roadmap item is about protecting existing behavior, not
  changing the behavior contract. The proposal therefore leaves
  `changes/workflow-recovery-regression-tests/specs/` empty.

- Reuse existing workflow runtime and event tests before adding new shared
  abstractions.
  Rationale: repository guidance favors minimal change, and M40 is the roadmap
  area for broader fixture standardization.

- Cover both success-path recovery behavior and failure-path retry exhaustion.
  Rationale: the workflow specs define both resumable and terminal outcomes as
  first-class observable behavior, so regression protection needs both.

- Use repository-standard `mvnd` commands for validation during this milestone.
  Rationale: the M39 roadmap notes explicitly standardize on `mvnd` for Maven
  validation in this phase.

- If the required full-suite gate fails outside the workflow recovery surface,
  fix only the smallest repository-local test harness issue needed to restore
  the gate.
  Rationale: this keeps the workflow within a passing verification state without
  silently broadening into unrelated product work.

## Alternatives Considered

- Create a new repository-wide workflow test harness first.
  Rejected because that shifts the change toward M40 test-infrastructure work.

- Start with `cross-interface-consistency-tests` instead.
  Rejected because M39 recommends this workflow recovery regression work before
  the broader cross-interface comparison change.

- Add delta spec files describing the test additions.
  Rejected because the proposal does not change user-visible behavior; a
  prose-only delta spec would misrepresent the change as a functional contract
  update.

- Stop after targeted workflow tests and leave the full-suite gate failing.
  Rejected because the change's testing tasks explicitly include the repository
  full unit suite, so the auto workflow cannot complete without addressing the
  blocking failure or obtaining scope approval.

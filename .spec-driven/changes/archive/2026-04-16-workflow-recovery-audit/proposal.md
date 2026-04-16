# workflow-recovery-audit

## What

- Add the remaining M37 workflow governance contract for workflow-level checkpoint persistence, runtime-driven recovery, supported automatic retry of retryable step failures, and recovery-oriented audit visibility.
- Extend the existing workflow runtime semantics so an interrupted workflow can continue under the same `workflowId` from the correct unfinished step instead of restarting from the beginning or losing in-flight waiting state.
- Keep the first recovery model runtime-driven rather than caller-driven: no new public SDK retry or recover entrypoint is added in this proposal.

## Why

- `workflow-runtime-contract`, `workflow-service-composition`, and `workflow-agent-human-bridge` established declaration, execution, pause, and resume semantics, but the current runtime still keeps workflow progress only in memory.
- That leaves the last M37 roadmap item unfinished: a supported interruption, process restart, or retryable transient failure can lose execution progress, repeat already completed work, or fail without enough audit detail to diagnose what happened.
- Finishing recovery and audit now completes the active workflow milestone in dependency order before the roadmap opens new areas such as production install/repair or profiled sandbox runtime.

## Scope

- In scope:
  - Define the observable contract for persisted workflow checkpoints at supported non-terminal execution boundaries.
  - Define runtime-driven recovery of running and waiting workflows under the same `workflowId` from the correct unfinished step boundary.
  - Define supported automatic retry behavior for retryable workflow step failures without advancing to later steps prematurely.
  - Define failure-diagnosis expectations for workflow result views and audit events when retries are attempted or exhausted.
  - Define the workflow recovery and retry audit event surface needed for checkpoint save, recovery, and retry scheduling visibility.
- Out of scope:
  - Adding a caller-triggered SDK/API `retryWorkflow(...)` or `recoverWorkflow(...)` operation.
  - General workflow authoring features such as branching, parallel steps, or custom retry expressions.
  - Replacing the existing question delivery, mobile delivery, or interactive command contracts.
  - Changing the spec-driven development loop, production install workflow, or environment profile isolation roadmap lines.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing SDK workflow declaration, start, state query, and result query operations remain the public workflow surface for the first recovery change.
- Workflows whose steps never report retryable failure continue to use the existing success, pause, resume, and terminal failure semantics.
- A later step still MUST NOT execute before the current step has either succeeded or failed terminally after supported retries are exhausted.
- This proposal does not redefine the workflow runtime as the spec-driven change loop or add a new operator control plane for manual workflow repair.

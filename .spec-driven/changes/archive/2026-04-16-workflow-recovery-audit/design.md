# Design: workflow-recovery-audit

## Approach

- Extend the existing workflow runtime contract around a recoverable execution boundary instead of adding a new caller-facing recovery API.
- Persist enough workflow progress to preserve observable behavior under supported interruption: the workflow identity, current status, unfinished execution boundary, and waiting-question correlation when the workflow is paused for input.
- Reuse the current ordered step model and add one explicit retryable-failure path so the runtime can retry the same step in place without changing later-step ordering semantics.
- Treat audit events and the workflow result view as the primary observability surfaces for checkpoint save, recovery, retry scheduling, and final retry exhaustion diagnosis.

## Key Decisions

- Use a runtime-driven recovery model.
  Rationale: the user explicitly accepted the recommendation to avoid expanding the public SDK/API surface in the first recovery change.
- Recover the same workflow instance rather than creating a replacement workflow instance.
  Rationale: preserving the same `workflowId` keeps state query, result query, question correlation, and audit history stable.
- Retry only the current failed step and only when that failure is explicitly marked retryable by the supported workflow step result contract.
  Rationale: this is the smallest observable retry surface that avoids hidden failure classification rules and preserves ordered step semantics.
- Keep checkpoint visibility and recovery observability in the existing workflow/event model.
  Rationale: M37 is about governance and auditability, so recovery state must be externally diagnosable rather than hidden behind an internal implementation detail.
- Keep retry policy intentionally narrow in the first change.
  Rationale: the milestone requires supported retries, not a generic policy engine or user-defined retry DSL.

## Alternatives Considered

- Add a caller-driven `retryWorkflow(...)` or `recoverWorkflow(...)` SDK operation now.
  Rejected because it expands the public control surface before the runtime-level recovery semantics are stable, and the user chose the runtime-driven path.
- Fail every interrupted workflow terminally and require callers to start a new workflow instance.
  Rejected because it breaks workflow identity continuity, duplicates prior successful work, and weakens auditability for business workflows.
- Retry later by re-running the whole workflow from step 0.
  Rejected because it can repeat already completed side-effecting steps and does not satisfy the roadmap's checkpoint-recovery intent.
- Infer retryability from exception text or transport-layer failure strings alone.
  Rejected because retry intent should be observable and testable at the workflow step contract boundary.

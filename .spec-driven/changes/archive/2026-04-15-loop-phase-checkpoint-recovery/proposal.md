# loop-phase-checkpoint-recovery

## What

Extend the autonomous loop persistence and resume contract from iteration-level
progress to phase-level checkpoints for the skill-based loop pipeline.

When a loop iteration is interrupted by a phase failure, timeout, question,
pause, or process stop, the loop will persist enough phase checkpoint state to
resume the same selected roadmap change from the correct next phase. Completed
phases must not be re-run, incomplete phases must not be skipped, and question
resume must continue to use the existing Question/Answer recovery path.

## Why

M35 already introduced the explicit phase pipeline and fresh phase session
boundaries. The remaining operational risk is recovery: a long-running loop can
now fail between `roadmap-recommend`, `spec-driven-propose`,
`spec-driven-apply`, `spec-driven-verify`, `spec-driven-review`, and
`spec-driven-archive`.

Without persisted phase checkpoints, recovery can only retry at the broader
iteration level. That can repeat side-effecting phases such as propose/archive
or skip required verification after a question. This change makes phase recovery
observable and testable before the later context-budget refinement work.

## Scope

In scope:

- Persist the selected change, milestone file, and completed phase set for the
  currently interrupted loop iteration.
- Recover a paused or interrupted iteration by resuming the same selected change
  from the first incomplete phase.
- Preserve existing `QUESTIONING` handling so automatic answers resume from the
  interrupted phase boundary and human-escalated questions remain retryable.
- Ensure checkpoint persistence is backward-compatible with existing progress
  snapshots that do not contain phase checkpoint data.
- Add focused unit tests for phase checkpoint save/load, stop/resume recovery,
  question pause/resume recovery, and failed/timed-out phase behavior.

Out of scope:

- Changing the manual `/roadmap-recommend` confirmation workflow.
- Rewriting the spec-driven phase skills or their business rules.
- Implementing cumulative context-budget semantic cleanup; that remains the
  later `loop-cumulative-context-budget` roadmap item.
- Adding a new human-in-loop UI or SQL/NL interaction surface.
- Making archive/propose idempotency guarantees beyond using persisted phase
  checkpoints to avoid re-running already completed phases.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- Phase order remains `RECOMMEND -> PROPOSE -> IMPLEMENT -> VERIFY -> REVIEW -> ARCHIVE`.
- Each non-skipped phase still starts with a fresh phase execution context.
- Loop auto recommend still selects only roadmap planned changes and preserves
  target milestone filtering.
- Completed changes are added to `completedChangeNames` only after the
  iteration completes successfully.
- Human-escalated questions must not mark the paused change complete.
- Existing `LoopIterationStore` callers and old stored progress snapshots remain
  loadable.

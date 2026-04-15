# loop-cumulative-context-budget

## What

Tighten the autonomous loop's cumulative context-budget semantics after the
phase-based skill pipeline and phase checkpoint recovery changes.

The loop already tracks token usage and persists active phase checkpoints. This
change makes the interaction between those two behaviors explicit: token usage
from every phase attempt must contribute to the loop lineage budget, recovery
must resume from the persisted cumulative baseline, skipped completed phases must
not be charged again, and context exhaustion must persist a retryable checkpoint
for any selected but incomplete roadmap change.

## Why

M35 has converted the autonomous loop into explicit skill phases with fresh
phase contexts and phase-level recovery. That reduces chat-history coupling, but
it also makes token accounting easier to get wrong: each phase can start in a new
session, a phase can fail or ask a question after consuming tokens, and recovery
can restart in a later process from a persisted checkpoint.

If cumulative budget semantics are not pinned down, the loop can under-count
interrupted work, double-count recovered baseline usage, or lose the selected
change checkpoint when context exhaustion occurs. This change closes the last
planned M35 item by making those behaviors observable and testable.

## Scope

In scope:

- Define cumulative token usage as loop-lineage usage across all phase attempts,
  including failed, timed-out, questioning, and retried attempts.
- Ensure recovery treats persisted `LoopProgress.tokenUsage` as the already
  applied budget baseline before executing any resumed phase.
- Ensure skipped completed phases are not re-executed and are not charged again
  during recovery.
- Ensure retrying an interrupted phase adds only the new retry attempt's token
  usage while preserving the previously persisted cumulative total.
- Ensure context exhaustion saves progress with the current cumulative usage and
  a retryable active phase checkpoint when a selected change is incomplete.
- Add focused unit tests for checkpoint recovery, question pause/resume,
  failed/timed-out phase retry, skipped-phase recovery, and context exhaustion
  token accounting.

Out of scope:

- Changing manual `/roadmap-recommend` confirmation behavior.
- Changing phase order or the `spec-driven-*` skill business rules.
- Replacing smart context optimization or token estimation algorithms.
- Adding a new UI, HTTP endpoint, JSON-RPC surface, or human-in-loop command.
- Changing provider-reported token usage semantics.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):

- Phase order remains `RECOMMEND -> PROPOSE -> IMPLEMENT -> VERIFY -> REVIEW -> ARCHIVE`.
- Every non-skipped phase still starts with a fresh phase execution context.
- Files, specs, repository state, and persisted loop snapshots remain the
  authoritative cross-phase handoff.
- Completed changes are added to `completedChangeNames` only after the selected
  change completes all required phases successfully.
- When no `ContextBudget` is configured, context-budget checks remain disabled.
- Old progress snapshots without token usage or checkpoint fields remain loadable
  with the existing default values.

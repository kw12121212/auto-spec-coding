# Tasks: loop-cumulative-context-budget

## Implementation

- [x] Review `DefaultLoopDriver`, `LoopProgress`, `LoopPhaseCheckpoint`, `LealoneLoopIterationStore`, and `SpecDrivenPipeline` token/checkpoint behavior before editing.
- [x] Ensure recovered `LoopProgress.tokenUsage()` initializes context-budget tracking exactly once before resumed phase execution.
- [x] Ensure incomplete selected changes persist both the active checkpoint and the cumulative token usage consumed so far.
- [x] Ensure skipped completed phases during checkpoint recovery are not re-executed and are not charged again.
- [x] Ensure failed, timed-out, questioning, and retried phase attempts add their reported token usage to the loop cumulative total without marking the change complete.
- [x] Ensure context exhaustion after a selected candidate is active saves a retryable checkpoint with the latest cumulative token usage before stopping.

## Testing

- [x] Add or update focused JUnit tests for cumulative token recovery, skipped-phase accounting, failed/timed-out retry accounting, human question pause/resume accounting, and context exhaustion checkpoint persistence.
- [x] Run validation command `node /home/wx766/.agents/skills/roadmap-recommend/scripts/spec-driven.js verify loop-cumulative-context-budget`.
- [x] Run focused unit test command `mvn test -q -Dtest="org.specdriven.agent.loop.*Test" -Dsurefire.useFile=false`.
- [x] Run full unit test command `mvn test -q -Dsurefire.useFile=false`.

## Verification

- [x] Confirm the implementation matches the delta spec and does not expand into unrelated loop, SDK, UI, HTTP, or provider changes.
- [x] Confirm old progress snapshots still load with default token/checkpoint values.
- [x] Confirm no planned roadmap item outside `loop-cumulative-context-budget` was implemented.

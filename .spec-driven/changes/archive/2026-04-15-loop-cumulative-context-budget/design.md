# Design: loop-cumulative-context-budget

## Approach

The implementation should treat persisted loop progress as the source of truth
for cumulative budget state. On start or resume, `DefaultLoopDriver` should load
`LoopProgress.tokenUsage()` once, initialize the context-budget tracker from that
value, and only add token usage returned by newly executed phase work after that
point.

Phase checkpoints and token accounting should be updated together. Whenever a
selected candidate is incomplete, saved progress should retain the active
checkpoint and the cumulative token total consumed so far. When the same
candidate resumes, completed phases from the checkpoint are skipped without
re-execution, and only the token usage from newly executed phases is added to the
persisted baseline.

The main code paths expected to be touched are the loop driver, progress record,
phase checkpoint handling, Lealone progress serialization, and focused loop
tests. The change should not alter provider clients or the smart context
optimizer; it consumes the token usage already reported by the pipeline and phase
runner results.

## Key Decisions

- Persisted progress is the cumulative budget boundary. This keeps fresh phase
  sessions from resetting budget state and avoids treating one phase's chat
  context as the loop's long-running budget.
- Failed, timed-out, questioning, and human-escalated phase attempts count toward
  cumulative usage when they consumed tokens. Those tokens were part of the loop
  lineage even if the selected change did not complete.
- Recovery must not replay the persisted baseline into the accumulator as new
  usage. The recovered value is the starting point; only subsequent execution
  adds to it.
- Context exhaustion during an incomplete selected change keeps the active
  checkpoint retryable. The loop should stop because the budget is exhausted, not
  silently mark the change complete or lose the selected candidate.
- Tests should use deterministic fake pipelines and stores where possible so they
  verify observable loop behavior without live LLM calls.

## Alternatives Considered

- Reset budget per phase: rejected because M35 explicitly uses fresh phase
  contexts while the budget is loop-lineage state.
- Count only successful completed iterations: rejected because failed or
  questioning phase attempts still consume provider context and must affect
  exhaustion decisions.
- Store separate per-phase token ledgers in this change: rejected as broader than
  needed. The observable requirement is correct cumulative accounting and
  checkpoint recovery, not a new reporting model.
- Rework smart context optimization: rejected because this change is about budget
  bookkeeping around the existing phase pipeline, not optimization quality.

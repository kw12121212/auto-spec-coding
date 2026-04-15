# Design: loop-phase-checkpoint-recovery

## Approach

Add a durable phase checkpoint to loop progress and use it as the authoritative
resume boundary for an interrupted autonomous loop iteration.

The checkpoint should capture the selected roadmap candidate for the in-flight
iteration and the ordered set of phases that completed successfully. On startup
or external resume, `DefaultLoopDriver` should recover that checkpoint before
selecting a new candidate. If a checkpoint exists, the driver should re-run the
pipeline for the same candidate with the completed phases supplied as skipped
phases. If no checkpoint exists, the driver keeps the current scheduling
behavior and asks the scheduler for the next eligible roadmap item.

Checkpoint state should be saved after each successfully completed phase is
reported by the pipeline and before the loop waits on human escalation or stops
due to an interruption. When the full iteration completes successfully, the
checkpoint should be cleared as part of the same progress snapshot that records
the completed change.

Persistence must remain compatible with old `loop_progress` rows. Missing
checkpoint fields should load as "no active checkpoint" so existing deployments
can continue without data migration.

## Key Decisions

- Treat the selected candidate and completed phases as the checkpoint boundary.
  This is the smallest durable state needed to avoid reselecting work, re-running
  completed phases, or skipping incomplete phases.
- Keep phase completion authoritative only after `SpecDrivenPipeline` reports a
  phase as completed. A phase interrupted by a question, failure, timeout, or
  process stop remains incomplete and must be eligible to run again.
- Store checkpoint state in loop progress rather than iteration history.
  Iteration history is an audit trail; progress is the source of truth for
  resume decisions.
- Preserve fresh phase sessions. Recovery should skip completed phases through
  the existing pipeline contract, then start the next phase with a fresh context.
- Keep context-budget cleanup out of this change. Token usage should continue to
  be preserved, but budget semantics across recovered phase sessions belong to
  the next roadmap item.

## Alternatives Considered

- Restarting interrupted work from the recommend phase every time. This was
  rejected because propose/archive and other phase side effects can be repeated.
- Persisting complete phase transcripts. This conflicts with M35's fresh context
  boundary and makes chat history part of recovery state.
- Adding separate checkpoint tables immediately. The current loop progress
  snapshot is already the resume source of truth; a new table can be considered
  later only if the data model outgrows a compact progress snapshot.
- Marking a change complete after the archive phase starts. Completion should
  remain tied to successful iteration completion, not phase entry.
- Folding cumulative token-budget changes into this work. That would mix two
  concerns and make recovery correctness harder to verify.

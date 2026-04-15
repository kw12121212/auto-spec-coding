# Design: loop-phase-session-reset

## Approach

Codify the phase boundary in the autonomous loop spec, then implement it at the
two places where phase work starts:

- Prompt-backed phases: each phase execution creates a new `Conversation`, a new
  `AgentContext` session identifier, and a new `QuestionRuntime` boundary for
  that phase. The first LLM call for a phase receives only the phase template and
  selected candidate prompt, not prior phase chat history.
- Command-backed phases: each phase execution starts a new command process in the
  configured project root. No process, stdin/stdout stream, or in-memory command
  state is reused between phases.

The loop still carries durable state through the selected `LoopCandidate`,
spec-driven artifacts on disk, repository state, and persisted loop progress.
The reset contract is therefore about conversational/session state, not about
discarding real project output.

## Key Decisions

- Treat files and persistence as the only authoritative cross-phase handoff.
  This matches M35's goal and makes later checkpoint recovery deterministic.
- Keep token usage accumulation outside the phase chat boundary. The loop still
  needs iteration-level token totals, but those totals must not imply chat
  transcript reuse.
- Preserve existing skip semantics. When a question is resolved and the pipeline
  resumes with completed phases skipped, the next phase still starts with fresh
  phase context.
- Avoid new public `LoopDriver` methods. Phase reset is a pipeline execution
  contract and should not expand the driver API.

## Alternatives Considered

- Reusing one `Conversation` across all phases and trimming it between phases.
  This was rejected because a trimmed transcript is still an inherited chat
  artifact and makes the boundary harder to verify.
- Persisting a generated summary between phases. This may be useful later as
  structured metadata, but it is not needed for this reset contract and would
  blur the source-of-truth rule.
- Moving phase reset into `DefaultLoopDriver`. The driver selects candidates and
  handles iteration lifecycle; the pipeline and phase runners own phase
  execution boundaries.
- Delaying this until checkpoint recovery. Recovery depends on knowing what
  state is valid to restore, so the reset contract should come first.

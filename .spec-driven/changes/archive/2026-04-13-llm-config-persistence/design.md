# Design: llm-config-persistence

## Approach

Add a minimal persistence layer beside the current in-memory runtime snapshot support.

The change should define:
- a runtime config store contract that can save and load the default non-sensitive `LlmConfigSnapshot`
- a Lealone-backed implementation that auto-creates its tables and records snapshot versions in append-only history
- recovery behavior that restores the last valid persisted default snapshot during initialization
- internal restore behavior that can move the active persisted default back to a prior stored version without introducing a new user-facing control surface yet

The delta spec should stay at observable behavior level. Mapping frontmatter can still point to the likely runtime config and event classes that will carry the implementation.

## Key Decisions

- Persist only the default runtime snapshot in this change.
  Session overrides already have an in-memory contract, but persisting them would pull in lifecycle and cleanup semantics that are not yet defined by the roadmap.

- Keep rollback as an internal capability.
  M28 needs version history now, but user-facing rollback commands would prematurely expand into SQL/API design and intersect with later governance work.

- Use append-only version history plus one active snapshot view.
  This keeps recovery and restore behavior observable without requiring mutation of prior history rows.

- Limit persisted fields to the existing non-sensitive snapshot contract.
  Secret reference resolution and redaction belong to M33 and should not be partially specified here.

## Alternatives Considered

- Start with `set-llm-sql-handler` first.
  Rejected because the command entry point would have to invent persistence semantics before the underlying storage contract exists.

- Persist both default and session-scoped snapshots together.
  Rejected because it would expand the proposal into session lifecycle policy before that behavior is clearly defined.

- Expose a rollback API in the same change.
  Rejected because it would widen the scope into external command surfaces and governance requirements that belong in later planned changes.

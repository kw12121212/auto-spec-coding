# set-llm-sql-handler

## What

Define the next increment of M28 by adding the observable `SET LLM` SQL entrypoint for runtime LLM configuration updates.

This proposal specifies how supported non-sensitive LLM parameters supplied through Lealone `SET LLM` statements are interpreted, scoped, and applied to later requests through the existing runtime snapshot model.

## Why

The repository already has runtime LLM snapshots and persistence for the default snapshot, but it still lacks the milestone's promised operator-facing control surface. Without `SET LLM`, runtime updates remain an internal capability instead of a Lealone-integrated feature.

Scoping this change to the SQL handler closes the main usability gap in M28 while keeping later work separate: provider refresh remains responsible for provider lifecycle details, and governance remains deferred to M33.

## Scope

In scope:
- observable `SET LLM` SQL behavior for supported non-sensitive runtime parameters
- parameter parsing and validation for fields already represented by the runtime LLM snapshot model
- session-scoped update semantics and fallback behavior relative to the default runtime snapshot
- interaction between successful `SET LLM` statements and later LLM request resolution
- failure behavior for unsupported or invalid `SET LLM` parameter values

Out of scope:
- secret-bearing parameters, vault resolution, redaction, permission checks, and audit governance
- adding new provider types or changing existing provider protocol semantics
- event publication for config changes
- cross-session broadcast behavior or cluster-wide propagation
- completing the internal provider refresh implementation beyond what must already be true for observable SQL behavior

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- direct runtime snapshot APIs remain available and keep their existing semantics
- persisted default snapshot history and restore behavior remain unchanged unless a successful default-scope `SET LLM` update writes a new default snapshot version
- in-flight LLM requests remain bound to the snapshot they resolved before the update
- secret governance remains outside this change; `SET LLM` in this proposal covers only non-sensitive fields

# Design: set-llm-sql-handler

## Approach

Extend the runtime LLM configuration contract with a Lealone-facing update surface rather than inventing a parallel config path.

The change should define observable behavior for:
- accepting `SET LLM` statements that carry supported non-sensitive parameter assignments
- mapping those assignments onto the existing runtime snapshot fields such as provider selection, model, base URL, timeout, and retry-related settings that are already part of the runtime config model
- applying successful updates to the current session scope by default so later requests in that session observe the replacement snapshot
- leaving in-flight requests untouched while later requests resolve the updated snapshot
- rejecting unsupported keys or invalid values without partially applying a mixed update

The delta spec should stay at behavior level. Mapping frontmatter can point to the current runtime config registry and store classes most likely to implement the change, without promising a specific parser class until implementation determines the narrowest fit.

## Key Decisions

- Reuse the existing runtime snapshot model instead of specifying a second mutable SQL-only config path.
  This keeps `SET LLM` as a front door to M28 behavior rather than a separate configuration system.

- Limit the SQL surface to non-sensitive parameters already covered by M28.
  Secret references and governance belong to M33 and should not be silently pulled into this proposal.

- Specify atomic all-or-nothing statement behavior.
  A successful `SET LLM` should install one coherent replacement snapshot; a failed statement should not leave partial parameter updates behind.

- Make session scope the default behavior for this change.
  The milestone notes already constrain the first increment to single-connection/per-session behavior, which keeps the proposal aligned with existing scope.

## Alternatives Considered

- Bundle `SET LLM`, provider refresh, and config-change events into one proposal.
  Rejected because those are separate roadmap items with independent verification points.

- Specify global/default-snapshot updates as the only SQL behavior.
  Rejected because the milestone notes explicitly bias the first increment toward connection/session-local updates.

- Allow arbitrary provider-specific keys through `SET LLM`.
  Rejected because it would weaken validation and make the spec dependent on implementation details rather than the known snapshot contract.

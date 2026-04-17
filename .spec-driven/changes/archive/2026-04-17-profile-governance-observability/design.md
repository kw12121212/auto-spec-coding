# Design: profile-governance-observability

## Approach

Extend the already-shipped profile runtime instead of introducing a second
governance subsystem.

The change is planned around three observable surfaces:

1. `LealonePlatform.sandlock()` remains the single profile-execution entry for
   Sandlock-backed launches, but now publishes a minimal audit event for each
   execution attempt.
2. `LealonePlatform.checkHealth()` adds Sandlock/profile readiness diagnostics so
   unsupported hosts, missing Sandlock entries, or invalid selected profiles are
   visible through the existing platform-health surface.
3. `BashTool` keeps its existing permission-first behavior for both direct-host
   and profile-backed execution paths, making the governance boundary explicit in
   the observable tool contract.

## Key Decisions

- Use the existing `EventBus` and event model for audit visibility.
  This keeps profile governance aligned with the rest of the platform instead of
  adding a profile-specific logging API.

- Put readiness diagnostics in `PlatformHealth` instead of inventing a separate
  profile diagnostics command.
  Operators already have a platform health surface, so profile readiness should
  appear there.

- Do not add a new profile-specific permission action.
  The roadmap item is about preserving permission boundaries around profile use,
  not redefining the permission model. Existing Bash permission checks stay the
  source of truth.

## Alternatives Considered

- Add a separate profile-governance service.
  Rejected because the platform already owns Sandlock-backed execution and health
  reporting.

- Limit observability to tool-specific logs.
  Rejected because profile execution is used below multiple command surfaces, and
  the audit contract should live at the shared execution layer.

- Start M25 production install work first.
  Rejected because dependency order favors finishing the nearly complete M38
  milestone before opening a new production-operations surface.

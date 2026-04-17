# profile-governance-observability

## What

Complete the remaining M38 governance work for profile-backed execution.

This change adds observable governance behavior around the existing Sandlock-backed
environment-profile runtime by defining:
- minimal audit events for profile execution attempts and outcomes
- platform health diagnostics for Sandlock/profile readiness
- explicit preservation of existing permission checks for Bash profile execution

## Why

M38 already ships the core environment-profile contract, Sandlock runner
integration, toolchain isolation, and tool-execution binding. The remaining gap
is governance: operators and developers can run commands under profiles, but the
system does not yet fully specify how those launches are audited, how profile
readiness appears in platform health, or how profile-backed execution is kept
inside existing permission boundaries.

Finishing this change closes the last planned item in M38 before the roadmap
moves into the broader production install/repair work of M25.

## Scope

In scope:
- audit-event requirements for Sandlock-backed profile execution success and failure
- platform health requirements for Sandlock/profile readiness diagnostics
- BashTool permission-boundary requirements for profile-backed execution
- focused unit and validation coverage for the updated observable behavior

Out of scope:
- new isolation mechanics or new profile-selection rules
- auto-installing Sandlock or supporting new host platforms
- changing the existing PermissionProvider decision model
- production deployment, remote repair, or service orchestration behavior

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing profile declaration, selection precedence, and isolation-field validation stay unchanged.
- Existing Sandlock failure modes continue to fail explicitly and must not fall back to direct host execution.
- Existing permission semantics remain unchanged: profile-backed Bash execution still uses the current permission check flow rather than introducing a new bypass path.

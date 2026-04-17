# Design: profile-tool-execution-binding

## Approach

- Reuse the existing environment-profile selection rules and Sandlock-backed execution capability as the source of truth for command isolation instead of creating a second profile-resolution path.
- Extend the observable command surfaces in small steps: `BashTool` gets an explicit optional `profile` selector, the existing background-process management contract can preserve a resolved profile when a caller already has one, and command-backed loop phases inherit the resolved project/default profile without a new override layer.
- Treat profile-backed execution as an all-or-nothing path when a profile has been resolved: if the selected or explicitly requested profile cannot be honored because Sandlock execution is unavailable or invalid, fail explicitly rather than silently running on the host.
- Preserve current host execution behavior only for repositories that do not resolve any environment profile for the surface being used.

## Key Decisions

- `BashTool` exposes an optional `profile` input parameter so ad hoc command callers can intentionally choose a non-default declared profile.
- The repository currently exposes background-process management through `ProcessManager`/`BackgroundProcessHandle` rather than a concrete profile-aware launch tool, so this change keeps scope small by adding resolved-profile propagation to that existing contract instead of inventing a new launch surface.
- Command-backed loop phases do not gain a loop-specific profile override in this change; they bind only to the resolved project/default profile to keep the proposal small and aligned with current loop configuration scope.
- The proposal keeps the observable fallback rule explicit: no resolved profile means current host execution behavior remains unchanged; a resolved profile with unavailable Sandlock prerequisites is a hard failure.

## Alternatives Considered

- Add a generic per-surface profile override everywhere at once. This was rejected because it would expand scope across Bash, background tools, and loop config before the default binding path is complete.
- Add governance and audit requirements first. This was rejected because governance is more useful after the execution path is actually profile-bound.
- Require profile-backed execution for every command even when the repository has no environment-profile configuration. This was rejected because it would turn an execution-binding change into a breaking migration for repositories that are not yet using profiles.

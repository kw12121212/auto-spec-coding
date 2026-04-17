# profile-tool-execution-binding

## What

- Bind `BashTool` execution to the repository environment-profile selection rules by adding an optional `profile` input and using the resolved default project profile when that input is omitted.
- Extend the existing background-process management contract so profile-bound launches can preserve and expose the resolved profile name in returned process metadata.
- Bind command-backed autonomous loop phases to the resolved project/default profile without adding a loop-specific override surface.

## Why

- Milestone `M38` already defines environment-profile declaration, Sandlock-backed execution, and toolchain isolation, but the roadmap still leaves a gap between those contracts and the command surfaces that actually launch work.
- Closing that gap now makes profile isolation observable in the three highest-risk execution paths: ad hoc shell commands, long-running/background processes, and loop-driven workflow commands.
- This change should come before governance and observability follow-up work so later auditing and diagnostics cover the real execution path rather than a partial configuration-only model.

## Scope

- Add an optional BashTool `profile` parameter and define explicit-profile, selected-profile, and failure behaviors.
- Define background-process resolved-profile propagation on the existing process-management contract and surface the resolved profile in the returned handle.
- Define loop phase command binding to the resolved project/default profile only.
- Preserve the current direct host execution behavior when a project does not resolve any environment profile.
- Keep the existing Sandlock runtime contract and environment-profile declaration model as the foundation for this change.
- Out of scope: new profile declaration fields, loop-specific profile overrides, background-process explicit per-launch profile overrides, production deployment workflows, and profile governance/audit expansion beyond the execution-binding behaviors needed here.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing timeout, stdout/stderr capture, permission checks, and non-zero-exit handling for `BashTool` remain in force.
- Existing background-process lifecycle tracking, readiness probing, and stop semantics remain in force.
- Existing autonomous loop phase order, fresh phase-session boundaries, and command template substitution remain in force.
- Existing direct command execution behavior remains available for repositories that do not resolve an effective environment profile.

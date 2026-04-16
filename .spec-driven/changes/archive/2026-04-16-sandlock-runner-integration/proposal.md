# sandlock-runner-integration

## What

- Add the first runnable M38 execution primitive: a platform-level Sandlock-backed
  command entry that can launch a supported command through a declared
  environment profile and return a structured execution result.
- Define the observable fast-fail behavior for Sandlock-unavailable,
  unsupported-host, and unknown-profile cases so later tool bindings do not
  silently fall back to the host environment.
- Keep the change at the platform/runtime boundary rather than binding it into
  `BashTool`, background processes, or loop phase execution yet.

## Why

- `environment-profile-contract` is already complete and gives the repository one
  stable profile namespace, but M38 still lacks the actual isolated execution
  entry that makes those profile names operational.
- Landing the Sandlock runner before toolchain isolation and tool binding follows
  the roadmap dependency order: first define how a named profile launches a
  process, then later define what each profile isolates and which existing tools
  must use it.
- Without this change, later M38 work would have to invent Sandlock launch and
  failure semantics independently across Bash, background-process, and loop
  command paths.

## Scope

In scope:
- Specify a typed `LealonePlatform` capability for Sandlock-backed command
  execution.
- Define how the capability resolves an explicit requested profile or falls back
  to the already-selected project environment profile from supported SDK/platform
  assembly.
- Define the structured execution result for launched commands, including the
  resolved profile identity and captured process outcome.
- Define explicit pre-launch failure behavior when Sandlock is unavailable, the
  host is unsupported, or the requested profile cannot be resolved.

Out of scope:
- PATH, HOME, cache-directory, and toolchain isolation details for each profile.
- Binding `BashTool`, background processes, or loop phase commands to the
  Sandlock capability.
- New permission or audit semantics for profile execution.
- Remote execution, deployment, production repair, or service restart behavior.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing `BashTool`, background-process, and `CommandSpecDrivenPhaseRunner`
  execution paths continue using their current host-environment behavior until a
  later M38 change binds them to profiles.
- The existing project YAML environment-profile declaration and selection rules
  remain the source of truth; this change does not introduce a second profile
  namespace or alternate profile file format.
- The SDK and platform public entry paths remain `SpecDriven.builder()` and
  `LealonePlatform.builder()`; this proposal only adds one new platform
  capability behind those existing assembly paths.

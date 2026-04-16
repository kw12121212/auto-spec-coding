# profile-toolchain-isolation

## What

Define the first observable per-profile toolchain isolation contract for M38.

This change extends the existing project environment profile and Sandlock-backed
execution specs so a selected profile does more than name a launch target. Each
profile will be able to describe an isolated execution environment for `PATH`,
`HOME`, and explicit cache roots, together with the bounded toolchain settings
needed for JDK, Node.js, Go, and Python execution.

The change keeps the work at the configuration and platform-runtime boundary. It
does not yet bind `BashTool`, background processes, or loop phases to profile
selection rules.

## Why

`environment-profile-contract` and `sandlock-runner-integration` already define
how the repository selects a profile and launches a Sandlock-backed command, but
they do not yet define what environment isolation a profile actually produces.
Without that contract, later tool-binding work can only pass a profile name
through the system without guaranteeing isolated Maven/npm/Go/pip state or
predictable toolchain resolution.

Landing isolation semantics now follows the roadmap order and reduces the risk
that `BashTool`, background-process, and loop execution paths each invent their
own profile environment rules. The accepted planning decisions for this change
are:
- The first isolation contract covers JDK, Node.js, Go, and Python.
- The contract keeps each family's required fields minimal and observable.
- The first cache boundary includes isolated `HOME` plus explicit cache roots
  for Maven, npm, Go, and pip.

## Scope

In scope:
- Extend the environment-profile spec to cover isolated execution settings in
  addition to profile selection.
- Define observable profile-scoped isolation for `HOME`, executable-search
  path settings, optional runtime environment overrides, and explicit cache
  roots.
- Define the first minimal toolchain-isolation contract for JDK, Node.js, Go,
  and Python profiles.
- Define how Sandlock-backed execution uses a profile's isolated home,
  declared runtime path, and cache roots.
- Define explicit pre-launch failure behavior when the selected profile cannot
  provide the required isolation settings.

Out of scope:
- Binding `BashTool`, background processes, or loop phase command execution to
  profiles.
- Permission, audit, and health-governance additions reserved for
  `profile-governance-observability`.
- Automatic installation of JDK, Node.js, Go, or Python runtimes.
- Expanding profiles into a general-purpose version manager or container
  platform.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing profile selection precedence remains explicit-profile first,
  otherwise the configured default profile.
- Existing Sandlock-unavailable, unsupported-host, and unknown-profile failure
  behavior remains in place.
- Existing `BashTool`, background-process, and loop command paths continue
  using their current execution behavior until a later M38 change binds them to
  profiles.

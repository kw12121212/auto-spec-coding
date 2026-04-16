# vendored-sandlock-binary

## What

- Vendor the current upstream Sandlock Linux x86_64 release binary into this
  repository under `depends/` and pin the platform runtime to that exact
  bundled version.
- Change Sandlock runner resolution so the default supported entry is the
  repository-bundled binary first, while still permitting an explicit
  `SPEC_DRIVEN_SANDLOCK_ENTRY` override when callers intentionally choose a
  different executable.
- Surface the bundled-version identity in the observable runtime contract so
  Sandlock availability and diagnostics are tied to a known repository-managed
  artifact instead of an unpinned host install.

## Why

- The current Sandlock runner integration requires a preinstalled host binary,
  which leaves execution behavior dependent on undeclared machine state and an
  unknown version.
- Vendoring one pinned binary matches the user's requirement to keep the file in
  this project, avoids separate installation steps for supported Linux x86_64
  development environments, and keeps the runner semantics reproducible.
- Using the repository-managed binary also creates a stable base for later M38
  work on toolchain isolation and tool binding, because those changes can target
  one known Sandlock version instead of whatever happens to be in `PATH`.

## Scope

In scope:
- Define repository-managed Sandlock binary discovery under `depends/` for the
  currently supported Linux x86_64 host/runtime path.
- Pin the first vendored binary to upstream release `v0.6.0` using the
  `sandlock-x86_64-unknown-linux-gnu.tar.gz` release asset as the repository
  source artifact.
- Define runtime precedence between an explicit `SPEC_DRIVEN_SANDLOCK_ENTRY`
  override and the repository-bundled default entry.
- Define explicit diagnostics when the bundled artifact is missing, not
  executable, or cannot be resolved on a supported host.
- Keep the change limited to platform-level Sandlock runner resolution and
  repository packaging for the pinned binary.

Out of scope:
- Adding non-Linux or non-x86_64 bundled Sandlock binaries.
- Automatic download/update workflows for future Sandlock releases.
- Toolchain isolation, profile policy definition, or Bash/background/loop
  binding.
- Signature verification infrastructure beyond pinning the exact upstream
  version and checked-in artifact path for this change.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing environment-profile declaration, selection precedence, and unknown
  profile failure behavior remain unchanged.
- `SPEC_DRIVEN_SANDLOCK_ENTRY` remains the explicit escape hatch for callers who
  intentionally need a non-bundled executable.
- Non-Linux hosts continue to fail explicitly as unsupported for Sandlock-backed
  execution.
- `BashTool`, background-process execution, and loop command execution remain
  out of scope and continue using their current behavior until later M38
  changes bind them to profiles.

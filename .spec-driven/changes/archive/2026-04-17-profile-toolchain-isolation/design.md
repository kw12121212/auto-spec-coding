# Design: profile-toolchain-isolation

## Approach

Update the existing M38 spec paths rather than creating a parallel profile
model. The change should extend:
- `.spec-driven/specs/config/environment-profile.md` for the observable profile
  declaration surface.
- `.spec-driven/specs/platform/sandlock-runner.md` for how Sandlock-backed
  execution consumes the selected profile's isolation settings.

The repository already has knowable code paths for these behaviors:
- `Config` and `ConfigLoader` for loading and validating profile declarations.
- `SdkBuilder` for exposing the selected profile through the assembled config
  map and platform capability.
- `LealonePlatform` for Sandlock-backed command execution.

The proposal should keep the isolation contract bounded and observable. The
profile model should support:
- A profile-scoped isolated `HOME`.
- A profile-scoped executable-search path declared explicitly through runtime
  profile settings rather than inherited from unrelated host defaults.
- Explicit cache roots for Maven, npm, Go, and pip.
- Minimal toolchain settings across JDK, Node.js, Go, and Python so all four
  supported families participate in the same contract.

The Sandlock execution contract should then require the launched process to use
those profile-scoped settings and to fail before launch when the selected or
requested profile cannot provide a valid isolated environment.

## Key Decisions

- Cover JDK, Node.js, Go, and Python in the first isolation contract.
  Rationale: this follows the accepted planning decision and keeps one shared
  profile surface for the repository's supported development stacks.

- Require isolated `HOME` and explicit Maven/npm/Go/pip cache roots.
  Rationale: `HOME` alone is not specific enough to guarantee observable cache
  separation for the toolchains M38 explicitly calls out.

- Keep isolation at the configuration and platform-runtime boundary.
  Rationale: the roadmap reserves BashTool/background-process/loop binding for
  `profile-tool-execution-binding`, so this change should define the runtime
  contract those later integrations depend on.

- Preserve explicit runtime path declaration instead of inferring PATH from the
  toolchain-family fields.
  Rationale: the implemented config model keeps PATH observable and bounded
  without turning this change into a toolchain-path inference engine.

- Preserve the current profile selection and Sandlock availability rules.
  Rationale: this change strengthens what a selected profile means; it does not
  introduce a second profile namespace or a fallback to direct host execution.

## Alternatives Considered

- Implement `profile-tool-execution-binding` first.
  Rejected because binding tools before defining isolated PATH/HOME/cache
  semantics would force those integrations to invent environment rules
  independently.

- Limit the first isolation contract to the stacks the repository uses most
  directly today.
  Rejected because the accepted planning decision was to cover JDK, Node.js,
  Go, and Python now, while keeping each family's field set minimal.

- Treat isolated `HOME` as sufficient and leave tool caches implicit.
  Rejected because M38 explicitly targets cross-project cache pollution, and the
  first contract should make Maven/npm/Go/pip cache roots observable rather
  than incidental.

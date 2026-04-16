# Design: sandlock-runner-integration

## Approach

- Extend `LealonePlatform` with one explicit Sandlock capability instead of
  changing existing tools directly. This keeps the change aligned with the
  roadmap order: first provide the runner primitive, then later bind existing
  tool surfaces to it.
- Reuse the existing environment-profile selection behavior already assembled by
  `SdkBuilder`. When callers omit an explicit profile at execution time, the
  Sandlock capability should use the effective selected profile already resolved
  from project YAML.
- Keep the Sandlock contract observable and CLI-agnostic. The spec should define
  supported launch behavior, result fields, and diagnostics without freezing the
  exact host command-line flags used to talk to Sandlock.
- Keep the proposal limited to foreground command execution and launch
  diagnostics. Profile-specific cache isolation, background lifecycle behavior,
  loop integration, and governance belong to later planned changes in M38.

## Key Decisions

- Use a platform capability, not a BashTool enhancement.
  Rationale: the roadmap already separates runner integration from later tool
  binding, and a platform capability gives Bash, background-process, and loop
  integration one shared execution primitive.

- Reuse the existing environment-profile names as the Sandlock launch identity.
  Rationale: `environment-profile-contract` already established the declared and
  selected profile namespace, so this change should not invent a parallel naming
  model.

- Fail explicitly instead of silently falling back to host execution.
  Rationale: M38 is about isolated execution. If Sandlock is missing,
  unsupported, or the profile reference is invalid, silently running on the host
  would hide the exact class of failure the milestone is meant to surface.

- Keep toolchain isolation out of scope.
  Rationale: the next planned M38 change (`profile-toolchain-isolation`) owns
  the per-profile PATH/HOME/cache semantics. Pulling those details into this
  change would collapse two planned changes into one.

## Alternatives Considered

- Start with `profile-tool-execution-binding` first.
  Rejected because binding existing tools before defining a shared Sandlock
  runner would force those tool paths to invent launch behavior and diagnostics
  independently.

- Put Sandlock launch semantics directly into `BashTool`.
  Rejected because that would leave background-process and loop command paths
  without the same primitive and would make the first M38 execution behavior too
  tool-specific.

- Include PATH/HOME/cache isolation in the same change.
  Rejected because the roadmap already allocates that work to
  `profile-toolchain-isolation`, and combining them would make this proposal
  larger and less reviewable than necessary.

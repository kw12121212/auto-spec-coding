# Design: environment-profile-contract

## Approach

Add a new config-facing spec for environment profiles under
`.spec-driven/specs/config/` and modify the existing config loader spec to make
profile declarations part of the supported project YAML surface.

The first contract should stay focused on declaration and resolution rather than
execution. The repository already has `Config`, `ConfigLoader`, and
`SdkBuilder` as knowable configuration entry paths, so the proposal should
anchor mappings there:
- `Config` and `ConfigLoader` for parsing and validation behavior
- `SdkBuilder` for resolving effective project configuration during assembly

The contract should define:
- A required default profile name in project YAML
- A map of named profiles keyed by profile name
- A stable selection order: explicit requested profile, otherwise configured
  default profile
- Observable diagnostics when the selected or default profile is missing or
  invalid
- A bounded first profile field set for JDK, Node.js, Go, and Python runtime
  declarations

Later M38 changes can extend this contract by attaching Sandlock launch
behavior, environment isolation, and tool execution binding to the already
defined profile model.

## Key Decisions

- Keep the first change in the config domain, not the execution domain.
  Rationale: this reduces cross-cutting risk and gives later M38 changes one
  shared profile model.

- Support only project YAML as the first profile source.
  Rationale: the repository already has YAML configuration loading, while
  dedicated profile files or multiple sources would add selection ambiguity.

- Require that resolution always ends on a valid profile.
  Rationale: the accepted user direction explicitly rejects a "no profile"
  state, so invalid or missing default-profile configuration must fail
  explicitly rather than silently falling back to the host environment.

- Do not bind profile execution into `BashTool` yet.
  Rationale: the roadmap already reserves that work for
  `profile-tool-execution-binding`, and pulling it into this change would blur
  milestone boundaries.

## Alternatives Considered

- Start with `sandlock-runner-integration` first.
  Rejected because Sandlock launch semantics would need to invent profile naming,
  config shape, and fallback rules before the repository had a shared contract.

- Put the first spec under platform or tools instead of config.
  Rejected because the first observable behavior is declaration, parsing,
  selection, and validation, not yet runtime execution behavior.

- Allow an implicit host-environment fallback when no profile is configured.
  Rejected because it conflicts with the accepted requirement that a selected
  profile must always exist, at least through the default profile.

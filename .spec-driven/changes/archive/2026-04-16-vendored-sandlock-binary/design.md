# Design: vendored-sandlock-binary

## Approach

- Add one repository-managed Sandlock artifact under `depends/` for the current
  supported platform target, rather than teaching runtime resolution to depend
  on ambient host package managers.
- Update the existing `SystemSandlockRuntime` resolution path so it prefers an
  explicit `SPEC_DRIVEN_SANDLOCK_ENTRY` override, then falls back to the pinned
  repository-managed executable, and only then reports the runner as
  unavailable.
- Keep the change repository-local and minimal: no new installer workflow, no
  platform matrix expansion, and no tool-binding changes.
- Add focused tests that verify bundled-path precedence, explicit override
  precedence, and explicit failure when the bundled entry is absent or not
  executable.

## Key Decisions

- Pin the first bundled version to upstream `v0.6.0`.
  Rationale: the user requested the current latest version, and pinning one
  exact version preserves reproducible behavior.

- Store the bundled artifact under `depends/`.
  Rationale: the user explicitly requested a repository-local dependency folder
  instead of requiring preinstallation or hiding the binary inside unrelated
  resource paths.

- Keep `SPEC_DRIVEN_SANDLOCK_ENTRY` as the highest-precedence override.
  Rationale: explicit caller intent should still win when debugging or testing a
  different binary, while the default path becomes reproducible for normal use.

- Limit the first bundled target to Linux x86_64.
  Rationale: that is the only currently requested and observed runtime target,
  and broadening the bundle matrix would enlarge the change without a present
  requirement.

## Alternatives Considered

- Continue requiring a host-installed `sandlock` on `PATH`.
  Rejected because it keeps version drift and setup burden outside the
  repository, which conflicts with the user's explicit requirement.

- Bundle the binary under `src/main/resources/builtin-tools/`.
  Rejected because that directory already represents a different built-in tool
  mechanism and the user explicitly asked for `depends/`.

- Add automatic download logic that fetches the binary on first use.
  Rejected because this change is about pinning and bundling a known version in
  the repository, not introducing a runtime network dependency or updater.

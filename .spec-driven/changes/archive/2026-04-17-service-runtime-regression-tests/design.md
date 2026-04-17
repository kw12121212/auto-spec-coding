# Design: service-runtime-regression-tests

## Approach

Add regression coverage directly around the current observable contracts already
defined in the main specs:
- `release/service-runtime-packaging.md`
- `api/service-http-exposure.md`

The implementation should prefer extending the existing focused test classes and
fixtures instead of introducing a new test framework layer. Expected touch points
are the current runtime and HTTP regression tests such as:
- `src/test/java/org/specdriven/agent/http/ServiceRuntimeLauncherTest.java`
- `src/test/java/org/specdriven/agent/http/HttpE2eTest.java`
- `src/test/java/org/specdriven/cli/SpecDrivenCliMainTest.java`

If a small supporting helper adjustment is required to keep tests stable and
readable, keep it local to the runtime test surface and avoid turning this change
into general test-infrastructure work.

## Key Decisions

- Treat this as a test-only change with no observable spec delta.
  Rationale: the milestone explicitly targets regression protection for existing
  behavior, not feature expansion.

- Reuse existing runtime and CLI tests before adding new shared abstractions.
  Rationale: the repo guidance favors minimal changes and M40 is the milestone for
  broader fixture standardization.

- Validate both structured success output and structured failure output.
  Rationale: M39 calls out startup configuration and error propagation as explicit
  regression targets, so failure-path assertions are first-class scope.

- Use repository-standard Maven daemon commands for verification.
  Rationale: the milestone notes explicitly standardize on `mvnd` for Maven-based
  validation in this phase.

## Alternatives Considered

- Create a new repository-wide runtime test harness first.
  Rejected because it shifts the change toward M40 test infrastructure work.

- Combine service runtime regression coverage with cross-interface consistency work.
  Rejected because the roadmap sequences those as separate planned changes and the
  runtime path is the narrower, higher-confidence first step.

- Add delta spec files describing new test expectations.
  Rejected because the proposal does not change user-visible behavior; adding
  prose-only delta specs would misrepresent this as a functional change.

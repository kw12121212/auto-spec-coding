# Design: full-test-isolation

## Approach

First, remove the highest-risk source of cross-test interference in the committed Maven test configuration: class-level JUnit parallel execution. Then rerun the full suite.

If any failures remain, apply the smallest targeted isolation fix to the specific shared default resource involved, preferring test-only configuration or unique per-test defaults over runtime behavior changes.

## Key Decisions

- Prefer a build-configuration fix before code changes because the observed failures come from shared embedded database state colliding under parallel classes.
- Keep method-level execution unchanged unless the suite still proves unsafe after removing class-level parallelism.
- Avoid changing production default JDBC behavior unless test-only stabilization is insufficient.

## Alternatives Considered

- Rewriting many tests to inject unique JDBC URLs was rejected as a larger change than the currently observed root cause requires.
- Disabling all JUnit parallel support was deferred unless class-level serialization alone proves insufficient.
- Changing the production default embedded JDBC URL was rejected because it would alter runtime behavior for callers who rely on defaults.

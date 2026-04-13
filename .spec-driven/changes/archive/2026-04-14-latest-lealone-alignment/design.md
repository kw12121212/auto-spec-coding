# Design: latest-lealone-alignment

## Approach

Treat this as an alignment change, not a product redesign.

Implementation should proceed in three passes:

1. Resolve the exact latest Lealone upstream source baseline to use for the refresh and make that baseline inspectable from committed repository materials.
2. Check every direct Lealone integration area that is likely to drift first: source compilation, service-executor SPI integration, embedded JDBC-backed stores, and code paths that rely on `com.lealone.orm.json` types.
3. Prefer the smallest code changes that restore compatibility and verification, then document the result and any clearly useful upstream capabilities that were discovered during the refresh.

The change should keep the current observable contracts stable. If an upstream API difference can be absorbed internally without changing project behavior, the compatibility fix should stay internal. If a new low-risk Lealone capability is adopted, it should be limited to places where the benefit is direct and the behavior remains easy to verify.

## Key Decisions

- Treat "latest Lealone" as a verified upstream source baseline, not just a version string. The proposal exists because `8.0.0-SNAPSHOT` alone is not enough to show which upstream state the repository is actually aligned to.
- Prioritize brittle integration points first. `LealoneSkillSourceCompiler` and `SkillServiceExecutorFactory` are more likely to break from upstream internal drift than the simpler embedded-JDBC stores, so they should be checked before broad cleanup work.
- Preserve existing observable behavior by default. Compatibility updates should repair the repository's fit with the refreshed Lealone baseline, not opportunistically redesign existing specs.
- Keep low-risk improvements narrow. Good candidates are replacing brittle internal hooks with stable upstream entry points when available, tightening repo-local verification, or documenting directly useful upstream capabilities for follow-on changes.
- Keep M32 out of scope. This refresh may inform later platform work, but it should not introduce a new platform layer or central runtime abstraction as part of a dependency-alignment task.

## Alternatives Considered

- Only change the declared Lealone version string and skip upstream-source verification. Rejected because the current dependency already uses `8.0.0-SNAPSHOT`; the real risk is source drift, not the label.
- Expand this work into the full M32 Lealone platform milestone. Rejected because that would turn a compatibility refresh into a multi-module architecture change.
- Limit the change to documentation only. Rejected because the user explicitly needs the repository updated to the latest Lealone source baseline and checked for real adaptation work.

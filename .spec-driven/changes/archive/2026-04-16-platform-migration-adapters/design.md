# Design: platform-migration-adapters

## Approach

- Reuse the existing `LealonePlatform` and `PlatformConfig` types as the single platform-facing assembly surface.
- Adapt public and SDK-owned Lealone-backed construction paths so they derive runtime configuration from the already assembled platform instead of recreating repository defaults in parallel.
- Express the migration in observable terms: default callers keep the same behavior, while callers that provide a custom `PlatformConfig` get consistent platform-backed behavior across adapted SDK services.
- Limit the change to thin adapter behavior and compatibility tests so M32 can be completed without turning the platform layer into a broad new abstraction project.

## Key Decisions

- Keep the scope on public and SDK-owned entry paths.
  Rationale: this is the smallest change that completes M32 and unblocks downstream milestones without forcing a repo-wide rewrite.
- Treat `PlatformConfig` as the single effective source for adapted Lealone-backed SDK assembly.
  Rationale: this removes duplicated defaults and gives later runtime features one stable platform baseline.
- Preserve the current typed `LealonePlatform` API.
  Rationale: M32 explicitly favors a thin glue layer, not a generic registry or portability abstraction.

## Alternatives Considered

- Migrate every Lealone-backed class in one change.
  Rejected because it is larger than needed to finish the milestone and raises regression risk.
- Leave scattered hardcoded defaults in place and rely on documentation.
  Rejected because later milestones would still inherit multiple assembly paths and inconsistent configuration behavior.
- Add a new generic platform service locator.
  Rejected because it conflicts with the existing typed platform contract and adds abstraction cost without a demonstrated need.

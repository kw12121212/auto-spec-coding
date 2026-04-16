# platform-migration-adapters

## What

- Add the final M32 migration step that routes remaining SDK-facing Lealone assembly through the existing `LealonePlatform` and `PlatformConfig` surface.
- Define the minimal backward-compatible adapter behavior needed to replace duplicated hardcoded platform defaults in public and SDK-owned assembly paths.
- Keep the platform layer as a thin integration boundary while making later milestones depend on one stable platform-backed entry path.

## Why

- M32 is already complete except for this planned change, and both M36 and M38 depend on M32 being finished first.
- The repository already exposes `LealonePlatform`, `PlatformConfig`, `SdkBuilder`, and `SpecDriven`, but some SDK-owned Lealone-backed assembly still reconstructs default JDBC settings instead of flowing through the assembled platform.
- Finishing the migration adapters now reduces dependency-order risk without opening the larger runtime and deployment scopes in later milestones.

## Scope

- In scope:
  - Specify how public SDK and platform entry paths share one effective platform configuration.
  - Specify compatibility-preserving migration of SDK-owned Lealone-backed helper assembly onto the platform-backed configuration path.
  - Add tests that cover default compatibility and custom `PlatformConfig` propagation through adapted entry paths.
- Out of scope:
  - New platform capability domains or new generic registry abstractions.
  - M25 production install/repair workflows.
  - M36 service application runtime features.
  - M37 workflow runtime features.
  - M38 Sandlock profile runtime features.
  - Big-bang migration of every Lealone-backed constructor in the repository regardless of whether it is reachable through the current public SDK surface.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- `SpecDriven.builder().build()`, `LealonePlatform.builder().buildPlatform()`, and existing `platform()` access remain supported.
- Existing observable SDK, agent, and platform behavior stays compatible when callers rely on default configuration.
- This change does not introduce a generic string-keyed or class-keyed platform lookup API.

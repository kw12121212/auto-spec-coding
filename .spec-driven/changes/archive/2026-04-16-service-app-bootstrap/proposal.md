# service-app-bootstrap

## What

- Add the first M36 runtime bootstrap contract for loading a supported declarative service application entry and converging startup idempotently.
- Define how `services.sql`-driven schema, service, and runtime bootstrap attaches to the existing `LealonePlatform` and `SpecDriven` assembly surfaces.
- Keep this change focused on application bootstrap behavior, not application HTTP exposure, deployment packaging, or broad schema-governance policy.

## Why

- M36 needs a stable bootstrap entry before `service-http-exposure`, `service-runtime-packaging`, and `service-schema-bootstrap-governance` can be specified coherently.
- The repository already has the closest building blocks in `SkillSqlConverter`, `SkillAutoDiscovery`, `SdkBuilder`, and `LealonePlatform`, but it does not yet define an application-level startup contract around them.
- Locking the bootstrap boundary now reduces overlap with M32 platform work and keeps later M36 changes focused on exposure, packaging, and governance instead of redefining startup each time.

## Scope

- In scope:
  - Specify a supported declarative application bootstrap entry centered on `services.sql`.
  - Specify idempotent startup behavior for repeated bootstrap against the same supported application inputs.
  - Specify how bootstrap uses the already assembled platform configuration instead of inventing a parallel runtime path.
  - Specify compatibility boundaries so existing SDK, JSON-RPC, and `/api/v1/*` agent API behavior remains unchanged.
- Out of scope:
  - Application-level HTTP exposure and method-to-HTTP mapping.
  - Runtime packaging, installer layout, or production deployment workflow.
  - Broad startup governance beyond the minimal bootstrap safety boundary needed to define supported inputs and failure behavior.
  - New non-Lealone service models or a second declarative application entry for this first change.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing `SpecDriven` SDK usage, JSON-RPC behavior, and `/api/v1/*` agent API remain compatible.
- Existing skill discovery and skill SQL behavior remain valid for their current use cases; this change adds an application bootstrap contract on top of those foundations.
- This change does not define or require new application HTTP routes.

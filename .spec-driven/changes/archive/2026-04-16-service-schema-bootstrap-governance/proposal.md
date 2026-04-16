# service-schema-bootstrap-governance

## What

- Define the minimum governance contract for automatic `services.sql` bootstrap and service-runtime startup configuration.
- Tighten the startup boundary so only supported idempotent bootstrap declarations are auto-applied, and unsupported or non-idempotent startup input is rejected before any bootstrap-managed statement runs.
- Define that runtime bind and platform settings remain sourced from explicit startup options and platform defaults, not from declarative bootstrap directives.

## Why

- M36 already has bootstrap, service HTTP exposure, and runtime packaging, but the governance boundary for what startup is allowed to auto-apply is still only implicit in the current code and tests.
- Before M25 production install/repair and M37 workflow runtime build on this surface, the repository needs an explicit safety contract for automatic startup behavior.
- Locking this boundary now reduces scope creep: later runtime features can depend on a governed startup path instead of redefining safety rules per feature.

## Scope

- In scope:
  - Specify the governed `services.sql` bootstrap contract for startup-time table and service creation.
  - Specify whole-input preflight validation and failure behavior for unsupported or incompatible startup declarations.
  - Specify the governed source of runtime startup settings such as bind address, port, JDBC URL, compile cache path, and API keys.
  - Preserve current runtime packaging and SDK/platform entry paths while clarifying their startup safety boundaries.
- Out of scope:
  - New supported SQL statement classes beyond idempotent bootstrap creation.
  - Production install/repair, service-manager integration, or remote deployment automation.
  - Workflow orchestration, human-in-loop business runtime, or sandbox/profile isolation.
  - A full migration engine or arbitrary runtime directive language inside `services.sql`.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing `/services/{serviceName}/{methodName}` and `/api/v1/*` behavior remains unchanged.
- Existing `SpecDriven`, `LealonePlatform`, and CLI service runtime entry paths remain the supported runtime surfaces.
- This change does not add new declarative application input formats beyond `services.sql`.

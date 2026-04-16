# Tasks: service-schema-bootstrap-governance

## Implementation

- [x] Update the M36 delta specs for platform bootstrap governance and service runtime configuration governance.
- [x] Define the automatic bootstrap boundary so full-file preflight validation happens before any bootstrap-managed statement executes.
- [x] Define the governed startup configuration contract so runtime bind and platform settings remain sourced from explicit runtime inputs and defaults, not `services.sql` directives.

## Testing

- [x] Run validation command `mvn -q -DskipTests compile`
- [x] Run focused unit test command `mvn -q -Dtest=LealonePlatformTest,SpecDrivenTest,ServiceRuntimeLauncherTest,SpecDrivenCliMainTest test`

## Verification

- [x] Verify the change stays within `service-schema-bootstrap-governance` scope and does not expand into M25 production install/repair or M37 workflow runtime behavior.
- [x] Verify the delta specs mirror the existing main spec paths and use repository-backed implementation/test mappings only.
- [x] Verify startup governance preserves the existing SDK, CLI, service HTTP namespace, and `/api/v1/*` compatibility contracts.

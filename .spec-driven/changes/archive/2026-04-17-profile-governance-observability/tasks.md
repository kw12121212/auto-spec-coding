# Tasks: profile-governance-observability

## Implementation

- [x] Add profile-execution audit event behavior and stable metadata to the shared Sandlock-backed execution path.
- [x] Extend platform health to report Sandlock/profile readiness as a dedicated observable subsystem.
- [x] Update BashTool profile-execution behavior so existing permission decisions are explicitly preserved for profile-backed launches.

## Testing

- [x] Run validation command `mvn -q validate -DskipBuiltinToolsDownload=true`
- [x] Run unit test command `mvn -q -Dtest=LealonePlatformTest,PlatformHealthTest,PlatformMetricsTest,BashToolTest test -Dsurefire.useFile=false -DskipBuiltinToolsDownload=true`

## Verification

- [x] Verify Sandlock-backed execution publishes auditable success and failure outcomes with stable profile metadata.
- [x] Verify platform health reports Sandlock/profile readiness without regressing the existing DB, LLM, compiler, or agent health semantics.

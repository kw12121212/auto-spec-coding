# Tasks: platform-migration-adapters

## Implementation

- [x] Update the M32 delta specs for platform and SDK compatibility behavior, including custom `PlatformConfig` propagation through adapted SDK-owned Lealone-backed services.
- [x] Refactor the remaining public and SDK-owned Lealone-backed assembly paths to use the assembled `LealonePlatform` or its effective `PlatformConfig` instead of duplicated hardcoded defaults.
- [x] Add compatibility-focused tests covering default behavior preservation and custom platform configuration across the adapted entry paths.

## Testing

- [x] Run validation command `mvn -q -DskipTests compile`
- [x] Run unit test command `mvn test`

## Verification

- [x] Verify default SDK and platform entry paths remain behaviorally compatible.
- [x] Verify adapted SDK-owned Lealone-backed services use the same effective platform configuration exposed by `sdk.platform()`.
- [x] Verify the change does not introduce new platform capability domains or generic lookup abstractions outside M32 scope.

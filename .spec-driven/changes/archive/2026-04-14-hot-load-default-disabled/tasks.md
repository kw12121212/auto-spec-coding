# Tasks: hot-load-default-disabled

## Implementation

- [x] Update the `skill-hot-loader` delta spec to require default-disabled activation and explicit programmatic enablement
- [x] Update the `skill-auto-discovery` delta spec to keep SQL registration working when hot-loading is disabled
- [x] Confirm implementation and test mappings stay limited to hot-loader, discovery, and executor fallback paths

## Testing

- [x] Run validation build with `mvn -q -DskipTests compile`
- [x] Run unit tests with `mvn -q -Dtest=SkillHotLoaderTest,SkillAutoDiscoveryTest,SkillServiceExecutorFactoryTest test`

## Verification

- [x] Run `node /home/code/.agents/skills/spec-driven-auto/scripts/spec-driven.js verify hot-load-default-disabled` and confirm the proposal artifacts are valid

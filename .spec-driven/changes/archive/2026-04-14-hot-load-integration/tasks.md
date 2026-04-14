# Tasks: hot-load-integration

## Implementation

- [x] Update `SkillAutoDiscovery` and `DiscoveryResult` to optionally hot-load matching
  executor Java sources during discovery while preserving current SQL registration
  semantics when no `SkillHotLoader` is configured
- [x] Update `SkillServiceExecutorFactory` to optionally prefer executor classes loaded
  from `SkillHotLoader.activeLoader(skillName)` and keep the current fallback path when
  no hot-loaded class is active
- [x] Add or update unit tests covering discovery hot-load success/failure/skip cases
  and executor-factory hot-loader preference/fallback behavior

## Testing

- [x] Run validation: `mvn -q -DskipTests compile`
- [x] Run unit tests: `mvn -q -Dtest=SkillAutoDiscoveryTest,SkillServiceExecutorFactoryTest test`

## Verification

- [x] Verify the delta specs, implementation mappings, and unchanged-behavior guarantees
  match the final code and tests

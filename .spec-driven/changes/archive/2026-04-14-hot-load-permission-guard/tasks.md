# Tasks: hot-load-permission-guard

## Implementation

- [x] Update the `skill-hot-loader` delta spec with permission-aware hot-load operation requirements, denied/confirmation failure behavior, and no-side-effect guarantees
- [x] Update the `skill-auto-discovery` delta spec so discovery supplies permission context and keeps hot-load permission failures isolated from SQL registration failures
- [x] Update the `permission-interface` delta spec with hot-load action/resource conventions and default-deny behavior
- [x] Add a visible hot-load permission failure type for denied and confirmation-required hot-load operations
- [x] Implement permission-aware `load`, `replace`, and `unload` behavior in the hot-loader contract and implementation
- [x] Add tests for allowed hot-load operations preserving existing compile/cache/registry behavior
- [x] Add tests for denied and confirmation-required hot-load operations producing visible failure and no compile/cache/registry side effects
- [x] Add discovery tests for authorized and unauthorized hot-load attempts while preserving SQL registration counts

## Testing

- [x] Run validation build with `mvn -q -DskipTests compile`
- [x] Run unit tests with `mvn -q -Dtest=SkillHotLoaderTest,SkillAutoDiscoveryTest,PermissionProviderTest,DefaultPermissionProviderWithStoreTest test`

## Verification

- [x] Run `node /home/wx766/.agents/skills/spec-driven-auto/scripts/spec-driven.js verify hot-load-permission-guard`
- [x] Verify the implementation keeps disabled hot-loading behavior and existing successful enabled behavior intact when permission allows the operation

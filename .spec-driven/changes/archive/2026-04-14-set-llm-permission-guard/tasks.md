# Tasks: set-llm-permission-guard

## Implementation

- [x] Add `PermissionProvider` field and constructor to `DefaultLlmProviderRegistry`
- [x] Add `requirePermission(sessionId, operation)` private method to `DefaultLlmProviderRegistry` following the `LealoneSkillHotLoader.requirePermission()` pattern
- [x] Insert permission check at the start of `applySetLlmStatement()` — throw `SetLlmSqlException` on DENY or CONFIRM
- [x] Insert permission check at the start of `clearSessionSnapshot()` — throw `SetLlmSqlException` on DENY or CONFIRM
- [x] Add `llm.config.set` default-deny rule to `DefaultPermissionProvider.check()`
- [x] Wire `PermissionProvider` into `SdkBuilder` so production-built registries receive the provider

## Testing

- [x] Run validation: `mvn compile -pl . -q`
- [x] Run unit tests: `mvn test -pl . -Dtest=DefaultLlmProviderRegistryTest -q`
- [x] Add test: `applySetLlmStatement_allowed_whenPermissionGranted` — mock provider returns ALLOW, mutation succeeds
- [x] Add test: `applySetLlmStatement_rejected_whenPermissionDenied` — mock provider returns DENY, `SetLlmSqlException` thrown, snapshot unchanged
- [x] Add test: `applySetLlmStatement_rejected_whenConfirmRequired` — mock provider returns CONFIRM, `SetLlmSqlException` thrown
- [x] Add test: `applySetLlmStatement_noCheck_whenPermissionProviderNull` — null provider, mutation succeeds (backward compat)
- [x] Add test: `clearSessionSnapshot_allowed_whenPermissionGranted` — mock provider returns ALLOW, clear succeeds
- [x] Add test: `clearSessionSnapshot_rejected_whenPermissionDenied` — mock provider returns DENY, override retained
- [x] Add test: `defaultPermissionProvider_deniesLlmConfigSet` — verify default-deny for `llm.config.set`
- [x] Add test: `defaultPermissionProvider_allowsLlmConfigSet_withStoredPolicy` — verify stored ALLOW overrides default-deny
- [x] `mvn test -pl . -q` — full test suite passes

## Verification

- [x] All new tests pass
- [x] All existing tests pass without modification (backward compatibility)
- [x] `mvn compile -pl .` succeeds with no warnings related to changed files
- [x] Delta specs cover all new behavior scenarios
- [x] No `SET LLM` mutation path exists without a permission guard when `PermissionProvider` is configured

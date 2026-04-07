# Tasks: permission-policy-store

## Implementation

- [x] Create `StoredPolicy` record in `org.specdriven.agent.permission` with fields: `id`, `permission` (Permission), `decision` (PermissionDecision), `createdAt`, `updatedAt`
- [x] Create `AuditEntry` record in `org.specdriven.agent.permission` with fields: `id`, `operation` (String: GRANT|REVOKE), `action`, `resource`, `requester`, `performedBy`, `timestamp`, `metadata` (Map)
- [x] Create `PolicyStore` interface in `org.specdriven.agent.permission` with methods: `grant`, `revoke`, `find`, `listPolicies`, `auditLog`
- [x] Implement `LealonePolicyStore` in `org.specdriven.agent.permission` — create `permission_policies` and `permission_audit_log` tables, implement all `PolicyStore` methods using Lealone embedded JDBC following `LealoneSessionStore` patterns
- [x] Add `PolicyStore` optional dependency to `DefaultPermissionProvider` — new constructor overload `(String workDir, PolicyStore store)`, update `check()` to query store first then fall back, update `grant()`/`revoke()` to delegate to store
- [x] Wire `LealonePolicyStore` creation in `DefaultOrchestrator`, pass to `DefaultPermissionProvider` constructor

## Testing

- [x] Lint: run `mvn compile` and fix any compilation errors
- [x] Unit tests: run `mvn test` and ensure all tests pass with no regressions
- [x] Unit test `LealonePolicyStore`: grant stores policy, revoke removes it, find returns stored decision, auditLog records entries, empty store returns empty optional
- [x] Unit test `DefaultPermissionProvider` with store: stored ALLOW overrides default CONFIRM, stored DENY overrides default ALLOW, no-store path unchanged
- [x] Unit test `LealonePolicyStore` concurrent access (grant + find from different threads)

## Verification

- [x] Verify `mvn test` passes with no regressions
- [x] Verify `DefaultPermissionProvider(String)` still works identically (no store)
- [x] Verify stored policies survive across `LealonePolicyStore` instances (persisted in DB)

# permission-policy-store

## What

Add a Lealone DB-backed policy store that persists permission policies at runtime and records grant/revoke audit entries. The existing `DefaultPermissionProvider` remains the default policy evaluator, but delegates to the DB store first when available.

## Why

Currently `DefaultPermissionProvider` is stateless — `grant()` and `revoke()` are no-ops. This is sufficient for single-session SDK embedding but inadequate for service deployments (M13 JSON-RPC, M14 HTTP REST API) where permissions must be configurable across sessions without redeployment. An audit trail of grant/revoke operations is also a security requirement for multi-user scenarios.

## Scope

- Define a `PolicyStore` interface with load/grant/revoke/list operations
- Implement `LealonePolicyStore` using Lealone embedded JDBC (following `LealoneSessionStore` patterns)
- Add a `permission_audit_log` table for grant/revoke audit entries
- Modify `DefaultPermissionProvider` to consult the policy store before applying default rules
- Wire `LealonePolicyStore` into `DefaultOrchestrator` alongside the existing `SessionStore`

## Unchanged Behavior

- When no policy store is configured, `DefaultPermissionProvider` behaves exactly as today (stateless, default policy only)
- `PermissionProvider`, `PermissionCheckHook`, and `ToolExecutionHook` interfaces remain unchanged
- Existing tool permission semantics (`permissionFor` implementations) are not modified
- The three-way decision model (ALLOW/DENY/CONFIRM) is preserved

# permission-policy-store — Design

## Approach

### PolicyStore interface

Add a `PolicyStore` interface in `org.specdriven.agent.permission` with methods:
- `grant(Permission, PermissionContext)` — persist an explicit ALLOW entry
- `revoke(Permission, PermissionContext)` — remove an explicit entry (or add a DENY override)
- `find(Permission, PermissionContext)` → `Optional<PermissionDecision>` — look up a stored decision
- `listPolicies()` → `List<StoredPolicy>` — list all active stored policies for admin/inspection
- `auditLog()` → `List<AuditEntry>` — return recent grant/revoke audit entries

### LealonePolicyStore implementation

Two tables:

1. `permission_policies` — stores active grant/deny overrides
   - `id` (VARCHAR PK), `action` (VARCHAR), `resource` (VARCHAR), `decision` (VARCHAR: ALLOW|DENY|CONFIRM), `constraints` (CLOB, JSON), `requester` (VARCHAR), `created_at` (BIGINT), `updated_at` (BIGINT)
   - Unique constraint on `(action, resource, requester)` — one stored decision per action-resource-requester triple

2. `permission_audit_log` — append-only audit trail
   - `id` (VARCHAR PK), `operation` (VARCHAR: GRANT|REVOKE), `action` (VARCHAR), `resource` (VARCHAR), `requester` (VARCHAR), `performed_by` (VARCHAR), `timestamp` (BIGINT), `metadata` (CLOB, JSON)

Follow `LealoneSessionStore` patterns:
- Embedded JDBC URL (`jdbc:lealone:embed:agent_db`)
- `CREATE TABLE IF NOT EXISTS` on init
- `MERGE INTO` for upserts
- JSON serialization for constraints/metadata maps using `com.lealone.orm.json.JsonObject`

### DefaultPermissionProvider integration

Add an optional `PolicyStore` field to `DefaultPermissionProvider`:
- Constructor overloads: existing `(String workDir)` unchanged, new `(String workDir, PolicyStore store)`
- `check()`: first queries `PolicyStore.find()`. If a stored decision exists, return it. Otherwise fall through to existing default rules.
- `grant()`: delegates to `store.grant()` (writes ALLOW policy + audit entry)
- `revoke()`: delegates to `store.revoke()` (removes policy entry or writes DENY override + audit entry)

### Orchestrator wiring

`DefaultOrchestrator` creates a `LealonePolicyStore` (sharing the same embedded DB as `LealoneSessionStore`) and passes it to `DefaultPermissionProvider`.

## Key Decisions

1. **DB-first, fallback to default** — stored policies take precedence over hardcoded defaults. This lets operators override even the conservative defaults (e.g., auto-allow specific bash commands) without changing code.
2. **Append-only audit log** — grant/revoke events are never deleted, only queried. Supports compliance and debugging.
3. **Optional store** — `DefaultPermissionProvider` works with or without a store. Zero-config SDK usage stays identical to today.
4. **No CONFIRM storage** — the store only persists ALLOW and DENY decisions. CONFIRM is always a runtime interaction, not a storable state.

## Alternatives Considered

- **In-memory only map** — simpler but loses policies on restart; rejected because service deployments need persistence.
- **Separate database per module** — `LealoneSessionStore` already uses `agent_db`; sharing it keeps operational overhead low and is consistent with the existing pattern.
- **EventBus-based audit** — could publish grant/revoke as events on the EventBus. Deferred to avoid coupling with M4's `event-audit-log` change; the audit table is self-contained for now and can be bridged later.

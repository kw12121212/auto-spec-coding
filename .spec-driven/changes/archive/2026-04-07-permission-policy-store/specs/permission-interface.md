# Permission Interface Spec — Delta

## ADDED Requirements

### Requirement: PolicyStore interface

- MUST be a public interface in `org.specdriven.agent.permission`
- MUST define `void grant(Permission permission, PermissionContext context)` to persist an ALLOW decision for the given permission and context
- MUST define `void revoke(Permission permission, PermissionContext context)` to remove any stored decision for the given permission and context
- MUST define `Optional<PermissionDecision> find(Permission permission, PermissionContext context)` to look up a stored decision
- MUST define `List<StoredPolicy> listPolicies()` to return all active stored policies
- MUST define `List<AuditEntry> auditLog()` to return recent grant/revoke audit entries

### Requirement: StoredPolicy record

- MUST be a Java record with fields: `id` (String), `permission` (Permission), `decision` (PermissionDecision), `createdAt` (long), `updatedAt` (long)

### Requirement: AuditEntry record

- MUST be a Java record with fields: `id` (String), `operation` (String), `action` (String), `resource` (String), `requester` (String), `performedBy` (String), `timestamp` (long), `metadata` (Map<String, String>)

### Requirement: LealonePolicyStore

- MUST implement `PolicyStore`
- MUST be a public class in `org.specdriven.agent.permission`
- MUST create `permission_policies` and `permission_audit_log` tables on initialization using `CREATE TABLE IF NOT EXISTS`
- MUST use Lealone embedded JDBC (`jdbc:lealone:embed:`) for all database operations
- `grant()` MUST persist an ALLOW decision and append a GRANT entry to the audit log
- `revoke()` MUST remove the stored policy entry and append a REVOKE entry to the audit log
- `find()` MUST return `Optional.empty()` when no stored policy matches
- `find()` MUST match policies by action, resource, and requester fields
- `listPolicies()` MUST return all rows from `permission_policies`
- `auditLog()` MUST return audit entries ordered by timestamp descending

### Requirement: DefaultPermissionProvider with PolicyStore

- The existing constructor `DefaultPermissionProvider(String workDir)` MUST continue to work without a PolicyStore, with grant/revoke as no-ops
- A new constructor `DefaultPermissionProvider(String workDir, PolicyStore store)` MUST accept an optional PolicyStore
- `check()` MUST query `PolicyStore.find()` first when a store is present; if a stored decision exists, return it without evaluating default rules
- `check()` MUST fall through to existing default rules when no stored decision exists or no store is configured
- `grant()` MUST delegate to `store.grant()` when a store is present
- `revoke()` MUST delegate to `store.revoke()` when a store is present

## UNCHANGED Requirements

- All existing requirements for PermissionProvider, Permission, PermissionContext, PermissionDecision, PermissionCheckHook, and ToolExecutionHook remain unchanged

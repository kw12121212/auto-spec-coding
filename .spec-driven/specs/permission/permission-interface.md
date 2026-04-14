---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/hook/PermissionCheckHook.java
    - src/main/java/org/specdriven/agent/hook/ToolExecutionHook.java
    - src/main/java/org/specdriven/agent/permission/AuditEntry.java
    - src/main/java/org/specdriven/agent/permission/DefaultPermissionProvider.java
    - src/main/java/org/specdriven/agent/permission/LealonePolicyStore.java
    - src/main/java/org/specdriven/agent/permission/Permission.java
    - src/main/java/org/specdriven/agent/permission/PermissionContext.java
    - src/main/java/org/specdriven/agent/permission/PermissionDecision.java
    - src/main/java/org/specdriven/agent/permission/PermissionProvider.java
    - src/main/java/org/specdriven/agent/permission/PolicyStore.java
    - src/main/java/org/specdriven/agent/permission/StoredPolicy.java
  tests:
    - src/test/java/org/specdriven/agent/hook/PermissionCheckHookTest.java
    - src/test/java/org/specdriven/agent/permission/DefaultPermissionProviderWithStoreTest.java
    - src/test/java/org/specdriven/agent/permission/LealonePolicyStoreConcurrentTest.java
    - src/test/java/org/specdriven/agent/permission/LealonePolicyStoreTest.java
    - src/test/java/org/specdriven/agent/permission/PermissionProviderTest.java
    - src/test/java/org/specdriven/agent/permission/PermissionTest.java
---

# Permission Interface Spec

## Requirements

### Requirement: PermissionProvider contract

- MUST define `check(Permission, PermissionContext)` returning `PermissionDecision`
- MUST define `grant(Permission, PermissionContext)`
- MUST define `revoke(Permission, PermissionContext)`

- MUST treat `PermissionDecision.ALLOW` as permission granted
- MUST treat `PermissionDecision.DENY` as permission rejected without further confirmation
- MUST treat `PermissionDecision.CONFIRM` as permission requiring an explicit approval step from a later hook or interface layer before execution may proceed
- Existing `PermissionDecision.ALLOW`, `PermissionDecision.DENY`, and
  `PermissionDecision.CONFIRM` meanings MUST remain unchanged for hot-load
  permissions
- Hot-load permission checks MUST use ordinary `Permission` and
  `PermissionContext` values

### Requirement: Permission record

- MUST be a Java record with fields: `action` (String), `resource` (String), `constraints` (Map<String, String>)

### Requirement: PermissionContext record

- MUST be a Java record with fields: `toolName` (String), `operation` (String), `requester` (String)

### Requirement: PermissionDecision enum

- MUST define exactly three outcomes: `ALLOW`, `DENY`, and `CONFIRM`
- `ALLOW` MUST mean the requested operation may proceed immediately
- `DENY` MUST mean the requested operation must not proceed
- `CONFIRM` MUST mean the requested operation is permitted only after an explicit confirmation step outside the core permission model

### Requirement: Default permission policy behavior

- The default permission policy MUST evaluate permissions deterministically from the requested action, resource, and context
- The default permission policy MUST return `CONFIRM` for bash execution requests
- The default permission policy MUST return `CONFIRM` for file mutation requests, including write and edit operations
- The default permission policy MUST return `ALLOW` for read and search operations targeting paths inside the active working directory tree
- The default permission policy MUST return `DENY` for read and search operations targeting paths outside the active working directory tree
- The default permission policy MUST return `DENY` for hot-load actions unless an
  explicit stored policy grants the requested permission
- The default permission policy MAY be extended by later changes, but those extensions MUST preserve the observable meanings of `ALLOW`, `DENY`, and `CONFIRM`

### Requirement: Hot-load permission naming

- `skill.hotload.load` MUST represent permission to load and potentially compile a
  dynamic skill executor for activation
- `skill.hotload.replace` MUST represent permission to replace and potentially
  compile a dynamic skill executor for activation
- `skill.hotload.unload` MUST represent permission to unload an active dynamic
  skill executor
- Hot-load permission resources MUST use the form `skill:<skillName>`
- Hot-load permission constraints MAY include operation metadata such as
  `entryClassName` or `sourceHash`, but constraints MUST NOT include raw Java source

### Requirement: Hook-based permission enforcement

- The orchestrator MUST invoke registered `ToolExecutionHook` instances before each tool execution
- A hook returning `ToolResult.Error` from `beforeExecute` MUST prevent the tool from being invoked
- A hook returning `null` from `beforeExecute` MUST allow execution to proceed to the next hook or the tool itself
- When the permission provider returns `PermissionDecision.DENY`, the `PermissionCheckHook` MUST return `ToolResult.Error` describing that permission was denied
- When the permission provider returns `PermissionDecision.CONFIRM`, the `PermissionCheckHook` MUST return `ToolResult.Error` describing that explicit confirmation is required
- When the permission provider returns `PermissionDecision.ALLOW`, the `PermissionCheckHook` MUST return `null` to allow execution

### Requirement: ToolExecutionHook interface

- MUST be a public interface in `org.specdriven.agent.hook`
- MUST define `ToolResult beforeExecute(Tool tool, ToolInput input, ToolContext context)` returning `null` to allow or `ToolResult.Error` to block
- MUST define `void afterExecute(Tool tool, ToolInput input, ToolResult result)` as a post-execution notification
- `afterExecute` MUST be called even when the tool returns `ToolResult.Error`
- `afterExecute` MUST NOT be called when `beforeExecute` blocks execution

### Requirement: PermissionCheckHook

- MUST implement `ToolExecutionHook`
- MUST be a public class in `org.specdriven.agent.hook`
- `beforeExecute` MUST call `tool.permissionFor(input, context)` to obtain the Permission, construct a `PermissionContext`, and delegate to `context.permissionProvider().check()`
- `afterExecute` MUST be a no-op

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

#### Scenario: default policy denies hot-load action without stored grant

- GIVEN a default permission provider with no stored policy for a hot-load action
- WHEN the provider checks `skill.hotload.load` on `skill:<skillName>`
- THEN it MUST return `DENY`

#### Scenario: stored policy can allow hot-load action

- GIVEN a default permission provider backed by a policy store
- AND the policy store contains an `ALLOW` decision for `skill.hotload.replace` on `skill:<skillName>` for the requester
- WHEN the provider checks that permission and context
- THEN it MUST return `ALLOW`

### Requirement: LLM config mutation permission action

The permission action `llm.config.set` MUST represent the right to modify runtime LLM configuration through `SET LLM` statements or equivalent mutation paths.

- `llm.config.set` MUST represent permission to change runtime LLM config for a targeted scope
- LLM config permission resources MUST use the form `session:<sessionId>` for session-scoped changes
- LLM config permission constraints MAY include operation metadata such as the mutation type

### Requirement: Default-deny policy for LLM config mutations

The default permission policy MUST return `DENY` for `llm.config.set` actions unless an explicit stored policy grants the requested permission.

#### Scenario: default policy denies LLM config mutation without stored grant
- GIVEN a default permission provider with no stored policy for an LLM config action
- WHEN the provider checks `llm.config.set` on `session:<sessionId>`
- THEN it MUST return `DENY`

#### Scenario: stored policy can allow LLM config mutation
- GIVEN a default permission provider backed by a policy store
- AND the policy store contains an `ALLOW` decision for `llm.config.set` on `session:<sessionId>` for the requester
- WHEN the provider checks that permission and context
- THEN it MUST return `ALLOW`

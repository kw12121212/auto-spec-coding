---
mapping:
  implementation:
    - src/main/java/org/specdriven/agent/agent/DefaultLlmProviderRegistry.java
  tests:
    - src/test/java/org/specdriven/agent/agent/DefaultLlmProviderRegistryTest.java
---

# Runtime LLM Config — Permission Guard Delta

## ADDED Requirements

### Requirement: SET LLM permission check before mutation

The system MUST check `llm.config.set` permission before applying a `SET LLM` statement mutation. When a `PermissionProvider` is configured, the registry MUST construct a `Permission` with action `llm.config.set` and resource `session:<sessionId>` and delegate to `permissionProvider.check()` before any snapshot replacement occurs.

#### Scenario: Allowed SET LLM proceeds normally
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `ALLOW` for `llm.config.set`
- WHEN `applySetLlmStatement(sessionId, sql)` is called
- THEN the statement MUST be applied and the replacement snapshot MUST become active for later requests

#### Scenario: Denied SET LLM is rejected without state change
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `DENY` for `llm.config.set`
- AND session `session-a` currently resolves runtime snapshot `S1`
- WHEN `applySetLlmStatement("session-a", sql)` is called
- THEN a `SetLlmSqlException` MUST be thrown describing the permission denial
- AND the active runtime snapshot for `session-a` MUST remain `S1`

#### Scenario: Confirm-required SET LLM is rejected
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `CONFIRM` for `llm.config.set`
- WHEN `applySetLlmStatement(sessionId, sql)` is called
- THEN a `SetLlmSqlException` MUST be thrown describing that explicit confirmation is required
- AND no state change MUST occur

#### Scenario: Missing PermissionProvider allows mutation for backward compatibility
- GIVEN a `DefaultLlmProviderRegistry` constructed without a `PermissionProvider`
- WHEN `applySetLlmStatement(sessionId, sql)` is called
- THEN the statement MUST be applied without any permission check
- AND existing behavior MUST be preserved

### Requirement: Clear session snapshot permission check

The system MUST check `llm.config.set` permission before clearing a session snapshot override. When a `PermissionProvider` is configured, the registry MUST construct a `Permission` with action `llm.config.set`, resource `session:<sessionId>`, and `operation = "clear"` before removing the session override.

#### Scenario: Allowed clear proceeds normally
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `ALLOW` for `llm.config.set`
- AND session `session-a` has a session-specific snapshot override
- WHEN `clearSessionSnapshot("session-a")` is called
- THEN the override MUST be cleared and a `LLM_CONFIG_CHANGED` event MUST be published

#### Scenario: Denied clear is rejected without state change
- GIVEN a `DefaultLlmProviderRegistry` with a `PermissionProvider` that returns `DENY` for `llm.config.set`
- AND session `session-a` has a session-specific snapshot override
- WHEN `clearSessionSnapshot("session-a")` is called
- THEN a `SetLlmSqlException` MUST be thrown
- AND the session override MUST remain in place

### Requirement: PermissionProvider constructor integration

`DefaultLlmProviderRegistry` MUST accept an optional `PermissionProvider` through its constructor chain. The existing constructors MUST remain backward-compatible.

#### Scenario: Constructor with PermissionProvider
- GIVEN a `PermissionProvider` instance
- WHEN `new DefaultLlmProviderRegistry(runtimeConfigStore, eventBus, permissionProvider)` is called
- THEN the registry MUST use the provided `PermissionProvider` for all mutation permission checks

#### Scenario: Existing constructor without PermissionProvider
- GIVEN no `PermissionProvider` is provided
- WHEN `new DefaultLlmProviderRegistry(runtimeConfigStore, eventBus)` is called
- THEN the registry MUST function identically to its pre-change behavior
- AND no permission checks MUST be performed on mutation operations

# set-llm-permission-guard

## What

Add permission-based access control to `SET LLM` SQL and other runtime LLM config mutation paths, following the same `PermissionProvider` + `PolicyStore` pattern established for skill hot-load governance (M34). Unauthorized `SET LLM` requests MUST be rejected with a `SetLlmSqlException` before any snapshot mutation occurs, and the rejection MUST be auditable.

## Why

M28 delivered dynamic runtime LLM config and M33's first change (`llm-config-vault-integration`) wired Vault-backed authentication. However, any caller with access to the SQL or SDK surface can currently execute `SET LLM` without restriction. In shared or production environments this is a security gap: an untrusted or compromised session could redirect LLM traffic to a hostile endpoint. This change closes that gap by requiring an explicit stored `ALLOW` policy before `SET LLM` (and `clearSessionSnapshot`) may proceed, defaulting to `DENY` — the same default-deny posture used for skill hot-load actions.

## Scope

**In scope:**
- New permission action `llm.config.set` for runtime LLM config mutation
- Permission check in `DefaultLlmProviderRegistry.applySetLlmStatement()` before snapshot replacement
- Permission check in `DefaultLlmProviderRegistry.clearSessionSnapshot()` before clearing override
- Default-deny policy for `llm.config.set` in `DefaultPermissionProvider`
- `SetLlmSqlException` wrapping for denied/confirm-required decisions
- Unit tests covering: allowed mutation, denied mutation, confirm-required mutation, missing provider graceful behavior
- Integration of `PermissionProvider` dependency into `DefaultLlmProviderRegistry` constructor

**Out of scope:**
- Secret redaction in events/logs (covered by `llm-config-secret-redaction`)
- Structured audit records beyond the existing `PolicyStore` audit log (covered by `llm-config-change-audit`)
- HTTP/JSON-RPC handler changes (they already map `SdkPermissionException` to 403 / `-32600`)
- New permission UI or management surface

## Unchanged Behavior

- `SET LLM` statement parsing and validation (`SetLlmStatementParser`) remains unchanged
- Snapshot immutability, atomic replacement, and in-flight request binding remain unchanged
- `LLM_CONFIG_CHANGED` event publication on successful changes remains unchanged
- Vault-backed authentication resolution remains unchanged
- Existing `PermissionProvider`, `PolicyStore`, and `PermissionCheckHook` contracts remain unchanged
- Tool-level permission checks through `PermissionCheckHook` remain unchanged
- Hot-load permission actions (`skill.hotload.*`) and their default-deny behavior remain unchanged

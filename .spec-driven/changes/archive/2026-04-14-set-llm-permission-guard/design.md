# Design: set-llm-permission-guard

## Approach

Follow the proven pattern from `LealoneSkillHotLoader.requirePermission()`:

1. **Add `PermissionProvider` dependency to `DefaultLlmProviderRegistry`** — extend the existing constructor chain to accept an optional `PermissionProvider`. When null, the registry retains backward-compatible behavior (no permission check, mutations allowed) so that existing tests and single-user setups continue working unchanged.

2. **Add permission check before mutation** — in `applySetLlmStatement()` and `clearSessionSnapshot()`, before any state change, construct a `Permission` with action `llm.config.set`, resource `session:<sessionId>`, and delegate to `permissionProvider.check()`. On `DENY` or `CONFIRM`, throw `SetLlmSqlException` with a descriptive message. On `ALLOW`, proceed with existing logic.

3. **Register default-deny rule in `DefaultPermissionProvider`** — extend the default policy to return `DENY` for any action starting with `llm.config.` when no stored policy exists, mirroring the hot-load default-deny pattern. A stored `ALLOW` policy in `PolicyStore` overrides this default.

4. **Permission context construction** — `PermissionContext` uses `toolName = "llm-runtime-config"`, `operation = "set"` (for `applySetLlmStatement`) or `operation = "clear"` (for `clearSessionSnapshot`), and `requester` propagated from the calling session context.

## Key Decisions

1. **Default-deny over default-confirm**: Unlike tool execution (which defaults to `CONFIRM`), LLM config mutation defaults to `DENY`. Rationale: a misconfigured LLM endpoint can silently exfiltrate data, which is worse than a blocked config change. This matches the hot-load default-deny posture.

2. **Null `PermissionProvider` means no check**: When the registry is constructed without a `PermissionProvider` (existing constructors), mutations proceed without permission checks. This preserves backward compatibility for single-user and test scenarios. Production deployments receive the `PermissionProvider` via `SdkBuilder`.

3. **Single action `llm.config.set` for both set and clear**: Using one action simplifies policy management — granting `llm.config.set` allows both setting and clearing session overrides. If finer granularity is needed later, it can be added without breaking the existing action.

4. **Resource format `session:<sessionId>`**: Consistent with the `skill:<skillName>` pattern used for hot-load. For default-scope changes (if any are added later), the resource would be `config:default`.

5. **Reuse `SetLlmSqlException` for permission failures**: Rather than introducing a new exception type, wrap permission denial in `SetLlmSqlException` with a clear message. This keeps the error surface consistent for callers who already handle this exception.

## Alternatives Considered

1. **Separate exception type `LlmConfigPermissionException`**: Rejected — would require changes in JSON-RPC and HTTP handlers to map the new exception type, adding scope for minimal benefit since `SetLlmSqlException` already maps correctly.

2. **`CONFIRM` as default instead of `DENY`**: Rejected — `CONFIRM` implies an interactive approval flow exists. The `SET LLM` SQL path has no such flow. Default-deny with explicit stored policy grant is the correct posture for a config mutation that can redirect network traffic.

3. **Separate actions for `llm.config.set` and `llm.config.clear`**: Rejected — adds policy management complexity for negligible security benefit in this phase. Can be split later if needed.

4. **Permission check in SQL handler layer instead of registry**: Rejected — the registry owns the mutation state; placing the check there ensures all mutation paths are covered regardless of entry point (SQL, SDK, future gRPC).

# Design: hot-load-permission-guard

## Approach

Extend the hot-loader activation boundary to consult the existing permission model before any side-effecting hot-load operation proceeds. The guarded operations are `load`, `replace`, and `unload`; `load` and `replace` also cover the hot-loader compilation path because they are the operations that may invoke `SkillSourceCompiler.compile(...)` on a cache miss.

Represent hot-load permission checks as normal `Permission` checks with operation-specific actions and skill-scoped resources. A caller-provided `PermissionContext` identifies the requester and operation. If the provider returns anything other than `ALLOW`, the hot-loader stops before compiling, reading or writing the cache, mutating the active registry, or mutating failed-skill state.

Keep `SkillAutoDiscovery` compatible with the guarded path by supplying a deterministic permission context when it invokes a configured hot-loader. Authorization remains policy-driven: an operator can allow or deny that requester through the existing permission provider/store behavior.

## Key Decisions

- Use `PermissionProvider` and `PermissionContext` instead of adding a hot-load-specific auth system. This keeps M34 aligned with the existing project-wide permission contract.
- Treat missing permission provider or missing caller context as not authorized once activation is enabled. Dynamic code activation should fail closed unless a trusted code path supplies an explicit authorization decision.
- Signal denied or confirmation-required hot-load operations with a hot-load permission exception. `unload` currently has no result type, so exception-based denial gives all guarded operations a consistent visible failure path and allows existing discovery error handling to keep reporting per-skill hot-load failures.
- Check permission after the default-disabled gate. Disabled activation already guarantees no compile/cache/registry side effects and should keep its existing ordinary failure result behavior.
- Defer trusted-source checks and audit logging to the later M34 planned changes so this proposal stays focused on permission gating.

## Alternatives Considered

- Add a new administrator role model only for hot-loading. Rejected because the project already has `PermissionProvider`, stored policies, and `PermissionDecision`.
- Rely only on the existing default-disabled activation flag. Rejected because explicit enablement does not authorize each later caller or operation.
- Return silent no-ops for denied `unload`. Rejected because permission denial must be observable and testable.
- Fold trusted-source activation and audit logging into this change. Rejected because M34 already splits those into separate planned changes, and combining them would expand scope prematurely.

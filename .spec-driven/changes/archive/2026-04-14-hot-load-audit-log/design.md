# Design: hot-load-audit-log

## Approach

Use the existing event/audit model as the observable audit surface. Add one hot-load audit event type, then have the hot-loader emit a structured event for each public hot-load operation attempt when an audit event sink is configured.

For `load` and `replace`, the emitted metadata should distinguish the operation outcome and the activation phase reached: disabled before permission, permission rejected, trust rejected, duplicate registration, cache hit, compile success, compile diagnostics failure, infrastructure failure, or active-registry success. For `unload`, the metadata should distinguish permission rejection, no-op unload, and successful removal.

The event metadata must stay within the existing event JSON constraints and must not include raw Java source. `sourceHash` is acceptable for `load` and `replace` because it identifies the source without exposing the source text.

## Key Decisions

- Reuse `EventBus` / `AuditLogStore` instead of adding a new audit-log abstraction. The repository already persists events through `LealoneAuditLogStore`, and M34 only needs traceability for hot-load operations.
- Emit a single public audit event per hot-load operation attempt. This keeps audit queries simple while still allowing the metadata to capture whether `load` or `replace` compiled source or reused cache output.
- Treat audit emission as a governance side effect, not as a violation of the existing no compile/cache/registry side-effect guarantees for rejected operations.
- Preserve existing constructors and runtime behavior where possible, while allowing production construction paths to wire an audit event sink when hot-loading can be enabled.
- Use requester information from `PermissionContext` when present. Disabled or missing-context paths may not know a requester, so the audit metadata should represent that explicitly instead of inventing one.

## Alternatives Considered

- Writing directly to `AuditLogStore` from the hot-loader was ruled out because it couples the hot-loader to a storage implementation instead of the existing event publication surface.
- Adding separate `SKILL_HOT_LOAD_LOAD`, `SKILL_HOT_LOAD_REPLACE`, and `SKILL_HOT_LOAD_UNLOAD` event types was ruled out in favor of one event type with an `operation` metadata field.
- Logging raw Java source for forensic detail was ruled out because it can expose sensitive or proprietary code and is unnecessary when `sourceHash` and outcome metadata are available.
- Auditing every low-level compiler call as a separate event was ruled out for this change because the roadmap item is hot-load governance; standalone compiler instrumentation can be proposed separately if needed.

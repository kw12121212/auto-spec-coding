# Design: llm-config-change-audit

## Approach

1. **New event type**: Add `LLM_CONFIG_CHANGE_REJECTED` to `EventType` enum alongside the existing `LLM_CONFIG_CHANGED`. This gives a clean semantic separation — `CHANGED` means the replacement is active, `REJECTED` means the attempt failed.

2. **Operator metadata**: Add an `operator` field to the metadata map of both event types. Derive the operator from the existing call context:
   - Session-scoped mutations (`applySetLlmStatement`, `clearSessionSnapshot`): `operator = "session:<sessionId>"`
   - Default-scope mutations (`replaceDefaultSnapshot`): `operator = "system"`

3. **Rejected event publishing**: In each failure path of `DefaultLlmProviderRegistry` (permission denied, parsing failure, validation failure), publish an `LLM_CONFIG_CHANGE_REJECTED` event *before* throwing the exception. The event carries the attempted scope, operator, result category, and human-readable reason.

4. **No new storage**: `LealoneAuditLogStore` already subscribes to all `EventType` values via `subscribeAll`, so the new event type is auto-persisted without code changes to the store.

## Key Decisions

- **Separate event type for rejections** — rather than reusing `LLM_CONFIG_CHANGED` with a `result` field. A separate type avoids semantic ambiguity (CHANGED implies a change occurred), lets consumers subscribe selectively, and matches the existing pattern where Vault operations have distinct `VAULT_SECRET_CREATED` / `VAULT_SECRET_DELETED` types rather than a single `VAULT_SECRET_CHANGED` with a result field.

- **Session ID as operator** — the session ID is already the natural identity context in the registry's mutation methods. No new parameter or ThreadLocal is needed. For default-scope changes, `"system"` is a sufficient operator label since those are programmatic, not user-initiated.

- **Result categories** — use discrete string values (`"denied"`, `"confirm_required"`, `"validation_failed"`, `"parse_error"`) rather than a boolean. This preserves more context for audit consumers without coupling to specific exception types.

## Alternatives Considered

- **Single event type with result field**: Could reuse `LLM_CONFIG_CHANGED` with `result = "success"/"failed"`. Rejected because the event name implies a change occurred; publishing a "CHANGED" event for a no-op rejection is semantically misleading and could confuse downstream consumers.

- **ThreadLocal operator context**: Could inject an operator through a thread-local variable for richer identity. Rejected as over-engineering for the minimum audit requirement — the session ID and `"system"` label are sufficient, and a ThreadLocal would add complexity without a clear consumer.

- **AuditEntry record extension**: Could add LLM-specific fields to `AuditEntry`. Rejected because the existing `Event` metadata map already carries all required audit fields, and `LealoneAuditLogStore` serializes the full event JSON, making the data queryable without schema changes.

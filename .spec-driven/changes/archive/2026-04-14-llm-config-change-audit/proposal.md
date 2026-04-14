# llm-config-change-audit

## What

Add minimum audit records for both successful and failed LLM configuration change attempts. This includes publishing a new `LLM_CONFIG_CHANGE_REJECTED` event type for failed changes (permission denied, validation failure) and adding an `operator` metadata field to all config change events so the audit trail identifies who initiated each change.

## Why

M33 done criteria require that all authorized config changes record audit information with at minimum: operator, timestamp, modification scope, and result. Currently the system only publishes `LLM_CONFIG_CHANGED` events on success — failed attempts (permission denied, validation failures) produce no auditable record. This gap means unauthorized probing of `SET LLM` cannot be traced after the fact. Additionally, existing events lack an operator field, so the audit trail cannot identify who triggered a change.

## Scope

- Add `LLM_CONFIG_CHANGE_REJECTED` enum value to `EventType`
- Publish `LLM_CONFIG_CHANGE_REJECTED` events when config mutation fails due to permission denial, validation error, or parsing failure
- Add `operator` metadata field to both `LLM_CONFIG_CHANGED` and `LLM_CONFIG_CHANGE_REJECTED` events (session-scoped: `session:<id>`, default-scope: `system`)
- Rejected event metadata includes: `scope`, `sessionId` (if session-scoped), `operator`, `result`, and `reason`

## Unchanged Behavior

- Existing `LLM_CONFIG_CHANGED` events continue to be published on success with their current metadata fields
- Permission check logic (`requirePermission`) and its deny/confirm/allow behavior remain unchanged
- Secret redaction in events, exception messages, and `toString()` remains unchanged
- `LealoneAuditLogStore` auto-persists the new event type without modification (it subscribes to all event types)
- In-flight request binding and snapshot isolation semantics remain unchanged

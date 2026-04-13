# provider-config-refresh

## What

- Define the remaining M28 change that makes runtime LLM snapshot updates affect future provider-backed requests created through the registry.
- Formalize snapshot-aware client creation for provider implementations and registry-managed session clients.

## Why

- M28 already covers snapshots, persistence, `SET LLM`, and config-change events, but it still needs an explicit contract for how later requests pick up refreshed provider parameters.
- Finishing this change completes the runtime hot-switching milestone and gives later governance work in M33 a stable behavior baseline.

## Scope

- In scope:
- Define how an existing registry-managed client resolves the active runtime snapshot at the start of each new request.
- Define how provider implementations create clients from a supplied runtime snapshot using refreshed non-sensitive fields.
- Define provider-switch behavior for later requests while preserving in-flight request binding.
- Out of scope:
- Secret reference resolution, redaction, permission checks, and audit governance.
- New provider types, new transport protocols, or changes to request/response wire semantics.
- New command surfaces beyond the existing runtime config entry points.

## Unchanged Behavior

Behaviors that must not change as a result of this change (leave blank if nothing is at risk):
- Existing provider authentication behavior remains owned by the registered provider configuration.
- Atomic replacement, session isolation, and in-flight request binding semantics remain unchanged.
- Existing OpenAI-compatible and Claude protocol behavior remains unchanged apart from which effective snapshot a later request resolves.

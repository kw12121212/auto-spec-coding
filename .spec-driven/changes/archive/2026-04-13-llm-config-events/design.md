# Design: llm-config-events

## Approach

Define event publication at the runtime LLM registry boundary, where successful config mutations already converge.

The proposal keeps the behavior narrowly scoped:
- one success event is published when a new runtime snapshot becomes active for future requests
- the covered mutation paths are default snapshot replacement, session snapshot replacement, `SET LLM`, and clearing a session override
- the event payload stays minimal and stable, identifying the affected scope and changed fields without exposing full snapshot bodies
- existing atomic replacement and in-flight binding semantics remain the source of truth for when an event may be emitted

The delta specs map this work to the current runtime registry and event surface instead of introducing a parallel config-observability layer.

## Key Decisions

- Publish `LLM_CONFIG_CHANGED` only for successful committed runtime changes.
  Failed validation or execution attempts belong to governance and audit work, not to this success-event contract.

- Cover exactly four runtime mutation paths in this proposal.
  This closes the observable gap for the runtime APIs already exposed by the registry while leaving startup recovery and version restore semantics unchanged.

- Keep metadata minimal: `scope`, `sessionId` when applicable, `provider`, and `changedKeys`.
  This gives downstream consumers enough routing context without committing the spec to full pre/post snapshot payloads.

- Encode `changedKeys` as a string-valued metadata field.
  The current event contract limits metadata values to scalar JSON types, so this proposal stays within the existing event-system shape instead of expanding the metadata type system.

- Route publication through `EventBus` injection into the runtime registry.
  The registry already centralizes runtime LLM mutations, making it the narrowest place to publish the event without duplicating logic across stores or providers.

## Alternatives Considered

- Publish events for failed updates as well as successful ones.
  Rejected because it would blur success-state observability with governance/audit concerns that belong to later roadmap items.

- Include full old/new snapshots in event metadata.
  Rejected because it would enlarge the observable contract, duplicate existing config state, and make later secret-governance work harder.

- Publish from the persistence store instead of the registry.
  Rejected because session-scoped changes and `SET LLM` do not naturally pass through the persistence store, while the registry already owns the observable runtime switch point.

- Expand the event-system metadata contract to support arrays or nested objects for `changedKeys`.
  Rejected because this change only needs lightweight field identification and should not broaden the generic event model.

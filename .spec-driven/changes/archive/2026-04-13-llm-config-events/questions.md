# Questions: llm-config-events

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Which successful operations should emit `LLM_CONFIG_CHANGED`?
  Context: The runtime registry exposes multiple mutation paths, so the proposal needs a precise event matrix.
  A: Emit the event only for successful committed changes from `replaceDefaultSnapshot`, `replaceSessionSnapshot`, `applySetLlmStatement`, and `clearSessionSnapshot`. Do not emit for failed attempts.

- [x] Q: What minimum metadata should the event carry?
  Context: Downstream consumers need a stable contract without over-specifying full snapshot payloads.
  A: Include `scope`, `sessionId` when applicable, `provider`, and `changedKeys`. Do not include full old/new snapshot values in this change.

- [x] Q: How should `EventBus` reach `DefaultLlmProviderRegistry`?
  Context: Runtime LLM mutations are centralized in the registry, but the current constructor surface does not include an event publisher.
  A: Use constructor injection of `EventBus` into `DefaultLlmProviderRegistry` so publication remains local to successful registry mutations.

# Design: provider-config-refresh

## Approach

Keep the registry-managed session client as a stable wrapper that resolves the effective runtime snapshot immediately before each `chat` or `chatStreaming` call. Bind each request to the snapshot it resolved at start, then delegate to the provider selected by that snapshot.

Use `LlmProvider.createClient(LlmConfigSnapshot)` as the narrow integration point for snapshot-aware refresh behavior. The snapshot supplies the effective non-sensitive request settings for the next request, while provider-owned authentication config stays attached to the registered provider instance.

## Key Decisions

- Resolve the active snapshot per request instead of mutating long-lived client instances in place.
- Keep snapshot-aware behavior on the existing provider interface instead of adding a new refresh command surface.
- Allow later requests to switch providers when the active snapshot changes, while requiring in-flight requests and streams to stay bound to the provider and snapshot chosen at request start.
- Keep this change limited to refresh semantics for non-sensitive config fields and leave governance to M33.

## Alternatives Considered

- Mutate existing provider or client instances in place after each config change. Rejected because it makes in-flight request guarantees harder to preserve.
- Add a separate explicit "refresh provider" API. Rejected because the existing snapshot-aware provider creation path already expresses the needed behavior with less surface area.
- Defer provider refresh semantics to the later governance milestone. Rejected because M33 depends on M28 already having a complete runtime-config behavior contract.

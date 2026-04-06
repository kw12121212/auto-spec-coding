# Questions: core-interfaces

## Open

<!-- No open questions -->

## Resolved

- [x] Q: Should `Tool.execute()` accept `Map<String, Object>` or a typed `ToolInput` record?
  Context: `Map<String, Object>` is flexible but untyped. A `ToolInput` record adds type safety but may be overly rigid for JSON-driven tool dispatch in M13/M14. Affects all M2–M3 tool implementations.
  A: Use `ToolInput` record. Provides extension space for future metadata (trace ID, timeout hints) without changing signatures.

- [x] Q: Should `EventBus.subscribe()` use Lealone's `AsyncHandler<T>` or a project-specific `Consumer<Event>`?
  Context: Using Lealone's type reduces coupling overhead but introduces a Lealone-common dependency in the event package. A project-native type keeps the event system framework-agnostic.
  A: Use `Consumer<Event>`. Keeps core interfaces framework-agnostic; event subscriptions are primarily synchronous in this project.

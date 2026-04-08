# Design: sdk-events

## Approach

Wire the existing `SimpleEventBus` into the SDK layer with two subscription scopes:

1. **Global listeners** — registered on `SdkBuilder.onEvent(Consumer<Event>)` or `SdkBuilder.onEvent(EventType, Consumer<Event>)`. These fire for every agent created by the `SpecDriven` instance. A shared `EventBus` is created during `build()` and passed to all `SdkAgent` instances.

2. **Per-agent listeners** — registered on `SdkAgent.onEvent(Consumer<Event>)` or `SdkAgent.onEvent(EventType, Consumer<Event>)`. These fire only for events produced by that specific agent's execution. The per-agent bus delegates to the global bus so global listeners still receive events.

During `SdkAgent.run()`, the internal `SdkInternalAgent` emits events:
- `AGENT_STATE_CHANGED` on each state transition (IDLE→RUNNING, RUNNING→STOPPED, etc.)
- `TOOL_EXECUTED` after each tool invocation with tool name, input summary, and result status in metadata
- `ERROR` on any exception during execution

`SdkEventListener` is a `@FunctionalInterface` extending `Consumer<Event>` for SDK-namespace clarity and future extensibility (default methods for filtering).

## Key Decisions

- **Use existing EventBus rather than a new callback system**: The internal `SimpleEventBus` already works. Wrapping it avoids duplicate pub/sub logic.
- **Global + per-agent bus hierarchy**: Users need both "log all agent events" and "watch this specific agent" patterns. A two-tier bus with delegation covers both.
- **SdkEventListener as separate type**: Even though it's functionally `Consumer<Event>`, a named type lets us add default methods later (e.g., `onToolExecuted(Consumer<Event>)`) without breaking API.

## Alternatives Considered

- **Listener per event type on SdkBuilder**: `onToolExecuted(...)`, `onStateChanged(...)`, etc. Rejected — too many methods for 10+ event types; hard to extend.
- **RxJava/Flow-based streaming**: Rejected — adds a dependency. `Consumer<Event>` is sufficient and consistent with the existing EventBus API.
- **Only global listeners, no per-agent**: Rejected — M13/JSON-RPC needs per-connection event isolation.

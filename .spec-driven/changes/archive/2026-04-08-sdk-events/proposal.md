# sdk-events

## What

Expose the internal `EventBus` through the SDK facade so SDK users can subscribe to agent runtime events (tool execution, state changes, errors, etc.) via typed callbacks on `SdkAgent` and `SdkBuilder`.

## Why

The SDK currently provides no visibility into what happens during `SdkAgent.run()` — users get a final string response but cannot observe tool calls, state transitions, or errors as they occur. This is a prerequisite for M13 (JSON-RPC) and M14 (HTTP REST), both of which need to stream events to external consumers.

## Scope

- Add `EventBus`-based subscription methods to `SdkAgent` (per-run event listeners)
- Add event subscription on `SdkBuilder`/`SpecDriven` (global listeners for all agents)
- Provide a typed `SdkEventListener` functional interface in `org.specdriven.sdk`
- Emit `AGENT_STATE_CHANGED`, `TOOL_EXECUTED`, and `ERROR` events during `SdkAgent.run()` lifecycle
- Update `sdk-public-api.md` spec with delta requirements

## Unchanged Behavior

- `SdkAgent.run(String)` return value and lifecycle management remain unchanged
- `SdkBuilder.build()` without event configuration still works identically
- Internal `EventBus` and `SimpleEventBus` APIs are not modified
- `SdkConfig` record signature is not changed (events are configured via builder, not config)
- Existing `EventType` enum values are not changed

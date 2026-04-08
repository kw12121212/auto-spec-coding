# Tasks: sdk-events

## Implementation

- [x] Create `SdkEventListener` functional interface in `org.specdriven.sdk` extending `Consumer<Event>`
- [x] Add `onEvent(SdkEventListener)` and `onEvent(EventType, SdkEventListener)` methods to `SdkBuilder` storing listeners
- [x] Create shared `SimpleEventBus` in `SpecDriven` during `build()`, wire global listeners into it
- [x] Add `onEvent(SdkEventListener)` and `onEvent(EventType, SdkEventListener)` methods to `SdkAgent`
- [x] Create per-agent `SimpleEventBus` in `SdkAgent` that delegates publishes to the global bus
- [x] Register per-agent listeners on the agent's bus on `onEvent()` calls
- [x] Emit `AGENT_STATE_CHANGED` events in `SdkInternalAgent` on each state transition (include `fromState`/`toState` in metadata)
- [x] Emit `TOOL_EXECUTED` events in the orchestrator callback with `toolName`, `success`, `durationMs` metadata
- [x] Emit `ERROR` events in `SdkInternalAgent` exception handling with `errorClass`/`errorMessage` metadata
- [x] Pass `EventBus` from `SpecDriven` to `SdkAgent` during `createAgent()`
- [x] Ensure backward compatibility: `build()` and `run()` without event registration work identically to before

## Testing

- [x] Lint: run `mvn compile` and verify zero compilation errors
- [x] Unit tests: run `mvn test -pl . -Dtest="SdkEventListenerTest,SdkAgentEventTest,SdkBuilderEventTest"`
- [x] Test: global wildcard listener receives all events from an agent run
- [x] Test: global typed listener receives only matching event types
- [x] Test: per-agent listener is scoped to that agent only
- [x] Test: both global and per-agent listeners fire for the same event
- [x] Test: AGENT_STATE_CHANGED events contain correct fromState/toState metadata
- [x] Test: TOOL_EXECUTED events contain toolName, success, durationMs metadata
- [x] Test: ERROR events contain errorClass and errorMessage metadata
- [x] Test: backward compatibility — run() without listeners works as before

## Verification

- [x] Verify implementation matches proposal scope
- [x] Verify no changes to existing EventBus/SimpleEventBus internal APIs
- [x] Verify SdkConfig record signature is unchanged

# event-system-types

## What

Provide a production-ready EventBus implementation and JSON serialization for Event records, completing the structured event system's core types.

## Why

M1 Done Criteria requires "事件类型可被实例化并序列化为 JSON". The `Event` record, `EventType` enum, and `EventBus` interface were delivered in `core-interfaces`, but two gaps remain:

1. No production EventBus implementation — `SimpleEventBus` exists only as a test inner class
2. No JSON serialization for `Event` records

Both are needed before downstream milestones (M4 agent lifecycle, M7 registries, M8 cron) can emit and persist structured events.

## Scope

- Production `SimpleEventBus` class in `org.specdriven.agent.event` (promote from test inner class)
- JSON serialization/deserialization for `Event` records using Lealone's built-in JSON support or a minimal hand-rolled approach
- Unit tests for EventBus and Event JSON round-trip

## Unchanged Behavior

- Existing `Event` record, `EventType` enum, and `EventBus` interface signatures must not change
- Existing tests must continue to pass
- No Lealone-specific dependencies in the event package beyond what's already used

# Event System Spec

## ADDED Requirements

### Requirement: Event record

- MUST be a Java record with fields: `type` (EventType), `timestamp` (long), `source` (String), `metadata` (Map<String, Object>)
- MUST be immutable

### Requirement: EventType enum

- MUST define at minimum: TOOL_EXECUTED, AGENT_STATE_CHANGED, TASK_CREATED, TASK_COMPLETED, CRON_TRIGGERED, ERROR
- MAY be extended in future milestones

### Requirement: EventBus pub/sub

- MUST support `subscribe(EventType, Consumer<Event>)` for event listeners
- MUST support `publish(Event)` to dispatch events to subscribers
- MUST support `unsubscribe(EventType, Consumer<Event>)` to remove listeners
- MUST use JDK `Consumer<Event>` as callback type, not Lealone's `AsyncHandler`

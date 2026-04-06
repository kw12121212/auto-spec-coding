# Event System Spec (delta: event-system-types)

## ADDED Requirements

### Requirement: SimpleEventBus production implementation

- MUST implement the `EventBus` interface
- MUST use `CopyOnWriteArrayList` for listener lists to ensure thread-safe iteration during publish
- MUST be a public class in `org.specdriven.agent.event`

### Requirement: Event JSON serialization

- MUST support `Event.toJson()` returning a JSON string with structure: `{"type":"<enum-name>","timestamp":<long>,"source":"<string>","metadata":{<key>:<value>,...}}`
- MUST support `Event.fromJson(String)` static factory to reconstruct an Event from its JSON representation
- MUST round-trip correctly: `Event.fromJson(event.toJson())` MUST produce an equal Event
- Metadata values MUST be limited to String, Number, and Boolean types
- MUST handle null/empty metadata as `{}`
- `Event` compact constructor MUST defensively copy the metadata map and normalize null to empty map

## UNCHANGED Requirements

### Requirement: Event record

- No changes to existing Event record fields or immutability

### Requirement: EventType enum

- No changes to existing enum values

### Requirement: EventBus pub/sub

- No changes to existing EventBus interface methods

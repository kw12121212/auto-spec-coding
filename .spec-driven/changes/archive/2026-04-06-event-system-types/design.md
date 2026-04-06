# Design: event-system-types

## Approach

1. **Promote SimpleEventBus to production** — Extract the `SimpleEventBus` inner class from `EventSystemTest` into a standalone public class in `org.specdriven.agent.event`. It already satisfies the `EventBus` contract with a `HashMap<EventType, List<Consumer<Event>>>` backend. Add thread-safety using `CopyOnWriteArrayList` per type to avoid concurrent modification during publish.

2. **JSON serialization** — Implement `Event.toJson()` (instance method) returning a JSON string, and `Event.fromJson(String)` static factory for deserialization. Use a minimal manual approach (no external JSON library dependency), serializing fields as: `{"type":"TOOL_EXECUTED","timestamp":12345,"source":"bash","metadata":{"exitCode":0}}`. The `metadata` map values are limited to primitives and strings for now.

## Key Decisions

- **No external JSON dependency**: Lealone's JSON utilities are in `lealone-common`, but inspecting the available API first. If Lealone provides a suitable `JsonValue`/`JsonObject` builder, use it. Otherwise, hand-roll minimal JSON to avoid pulling in Jackson/Gson.
- **CopyOnWriteArrayList for thread safety**: EventBus may be called from multiple VirtualThreads. `CopyOnWriteArrayList` gives safe iteration during publish without explicit locking, and subscription changes are expected to be infrequent relative to publishes.
- **Static factory on Event for deserialization**: Keeps the record immutable and avoids a separate builder/factory class.

## Alternatives Considered

- **ConcurrentHashMap + synchronized lists**: More complex, no benefit over CopyOnWriteArrayList for read-heavy publish patterns.
- **Separate EventSerializer class**: Over-engineered for two methods; keeping serialization on the Event record itself is simpler and co-located.
- **Jackson/Gson dependency**: Rejected — adds a dependency when the serialization format is trivial and well-known.

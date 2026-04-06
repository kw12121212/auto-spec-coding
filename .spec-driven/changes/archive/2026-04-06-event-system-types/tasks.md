# Tasks: event-system-types

## Implementation

- [x] Create `SimpleEventBus` as a public class in `org.specdriven.agent.event`, implementing `EventBus` with `CopyOnWriteArrayList`-backed listeners per `EventType`
- [x] Add `toJson()` instance method to `Event` record, producing `{"type":"...","timestamp":...,"source":"...","metadata":{...}}`
- [x] Add `fromJson(String)` static factory method to `Event` record, parsing the same JSON format
- [x] Refactor `EventSystemTest` to use the production `SimpleEventBus` instead of the test inner class, removing the inner class

## Testing

- [x] Unit tests for `SimpleEventBus`: subscribe, publish, unsubscribe, type isolation, concurrent publish safety
- [x] Unit tests for `Event.toJson()`: all EventType values, metadata with primitives, empty metadata
- [x] Unit tests for `Event.fromJson()`: round-trip with toJson, malformed input handling
- [x] `mvn test` passes (all 40+ existing tests + new tests)

## Verification

- [x] `Event` can be instantiated and serialized to JSON (M1 done criteria)
- [x] No external dependencies added beyond existing Lealone and JUnit 5
- [x] `EventBus` interface unchanged, `SimpleEventBus` is a new additive class

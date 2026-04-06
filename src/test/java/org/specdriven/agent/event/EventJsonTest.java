package org.specdriven.agent.event;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;

import org.junit.jupiter.api.Test;

class EventJsonTest {

    @Test
    void toJsonAllFields() {
        Event event = new Event(EventType.TOOL_EXECUTED, 12345L, "bash", Map.of("exitCode", 0));
        String json = event.toJson();
        assertEquals("{\"type\":\"TOOL_EXECUTED\",\"timestamp\":12345,\"source\":\"bash\",\"metadata\":{\"exitCode\":0}}", json);
    }

    @Test
    void toJsonEmptyMetadata() {
        Event event = new Event(EventType.ERROR, 99L, "test", Map.of());
        String json = event.toJson();
        assertTrue(json.contains("\"metadata\":{}"));
    }

    @Test
    void toJsonNullMetadata() {
        Event event = new Event(EventType.ERROR, 99L, "test", null);
        String json = event.toJson();
        assertTrue(json.contains("\"metadata\":{}"));
    }

    @Test
    void toJsonStringMetadataValue() {
        Event event = new Event(EventType.AGENT_STATE_CHANGED, 1L, "agent-1", Map.of("state", "running"));
        String json = event.toJson();
        assertTrue(json.contains("\"state\":\"running\""));
    }

    @Test
    void toJsonBooleanMetadataValue() {
        Event event = new Event(EventType.TASK_COMPLETED, 1L, "task", Map.of("success", true));
        String json = event.toJson();
        assertTrue(json.contains("\"success\":true"));
    }

    @Test
    void toJsonAllEventTypes() {
        for (EventType type : EventType.values()) {
            Event event = new Event(type, 1L, "src", Map.of());
            String json = event.toJson();
            assertTrue(json.contains("\"type\":\"" + type.name() + "\""),
                    "Missing type for " + type);
        }
    }

    @Test
    void toJsonEscapesSpecialChars() {
        Event event = new Event(EventType.ERROR, 1L, "src\n\"test\"", Map.of());
        String json = event.toJson();
        assertTrue(json.contains("\\n"));
        assertTrue(json.contains("\\\""));
    }

    @Test
    void fromJsonRoundTrip() {
        Event original = new Event(EventType.TOOL_EXECUTED, 12345L, "bash", Map.of("exitCode", 0));
        Event restored = Event.fromJson(original.toJson());
        assertEquals(original.type(), restored.type());
        assertEquals(original.timestamp(), restored.timestamp());
        assertEquals(original.source(), restored.source());
        assertEquals(original.metadata(), restored.metadata());
    }

    @Test
    void fromJsonRoundTripAllTypes() {
        for (EventType type : EventType.values()) {
            Event original = new Event(type, 42L, "src", Map.of("key", "val"));
            Event restored = Event.fromJson(original.toJson());
            assertEquals(original, restored, "Round-trip failed for " + type);
        }
    }

    @Test
    void fromJsonWithBooleanMetadata() {
        Event original = new Event(EventType.TASK_COMPLETED, 1L, "t", Map.of("ok", true, "flag", false));
        Event restored = Event.fromJson(original.toJson());
        assertEquals(true, restored.metadata().get("ok"));
        assertEquals(false, restored.metadata().get("flag"));
    }

    @Test
    void fromJsonEmptyMetadata() {
        Event original = new Event(EventType.ERROR, 1L, "src", Map.of());
        Event restored = Event.fromJson(original.toJson());
        assertTrue(restored.metadata().isEmpty());
    }

    @Test
    void fromJsonMalformedThrows() {
        assertThrows(IllegalArgumentException.class, () -> Event.fromJson("not json"));
        assertThrows(IllegalArgumentException.class, () -> Event.fromJson("{}"));
        assertThrows(IllegalArgumentException.class, () -> Event.fromJson("{\"type\":123}"));
    }

    @Test
    void fromJsonMissingSourceThrows() {
        String json = "{\"type\":\"ERROR\",\"timestamp\":1,\"metadata\":{}}";
        assertThrows(IllegalArgumentException.class, () -> Event.fromJson(json));
    }
}

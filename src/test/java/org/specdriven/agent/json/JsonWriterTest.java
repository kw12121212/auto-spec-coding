package org.specdriven.agent.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonWriterTest {

    @Test
    void emptyObject() {
        assertEquals("{}", JsonWriter.object().build());
    }

    @Test
    void emptyArray() {
        assertEquals("[]", JsonWriter.array().build());
    }

    @Test
    void stringField() {
        String json = JsonWriter.object().field("key", "value").build();
        assertEquals("{\"key\":\"value\"}", json);
    }

    @Test
    void multipleFields() {
        String json = JsonWriter.object()
                .field("a", "1")
                .field("b", 2L)
                .field("c", true)
                .build();
        assertEquals("{\"a\":\"1\",\"b\":2,\"c\":true}", json);
    }

    @Test
    void longField() {
        String json = JsonWriter.object().field("n", 42L).build();
        assertEquals("{\"n\":42}", json);
    }

    @Test
    void doubleField() {
        String json = JsonWriter.object().field("t", 0.7).build();
        assertTrue(json.contains("\"t\":0.7"));
    }

    @Test
    void nullStringField() {
        String json = JsonWriter.object().field("k", (String) null).build();
        assertEquals("{\"k\":null}", json);
    }

    @Test
    void rawField() {
        String json = JsonWriter.object().rawField("inner", "{\"x\":1}").build();
        assertEquals("{\"inner\":{\"x\":1}}", json);
    }

    @Test
    void arrayField() {
        String json = JsonWriter.object()
                .arrayField("items", List.of("\"a\"", "\"b\""))
                .build();
        assertEquals("{\"items\":[\"a\",\"b\"]}", json);
    }

    @Test
    void arrayElement() {
        String json = JsonWriter.array().element("\"x\"").element("\"y\"").build();
        assertEquals("[\"x\",\"y\"]", json);
    }

    @Test
    void stringEscaping() {
        String json = JsonWriter.object().field("s", "say \"hello\"\nworld").build();
        assertEquals("{\"s\":\"say \\\"hello\\\"\\nworld\"}", json);
    }

    @Test
    void backslashEscaping() {
        String json = JsonWriter.object().field("p", "C:\\Users").build();
        assertEquals("{\"p\":\"C:\\\\Users\"}", json);
    }

    @Test
    void tabEscaping() {
        String json = JsonWriter.object().field("t", "a\tb").build();
        assertEquals("{\"t\":\"a\\tb\"}", json);
    }

    @Test
    void fromMapEmpty() {
        assertEquals("{}", JsonWriter.fromMap(null));
        assertEquals("{}", JsonWriter.fromMap(Map.of()));
    }

    @Test
    void fromMapString() {
        String json = JsonWriter.fromMap(Map.of("type", "object"));
        assertTrue(json.contains("\"type\":\"object\""));
    }

    @Test
    void fromMapNested() {
        Map<String, Object> inner = Map.of("type", "string");
        Map<String, Object> outer = Map.of("props", inner);
        String json = JsonWriter.fromMap(outer);
        assertTrue(json.contains("\"props\":{\"type\":\"string\"}"));
    }

    @Test
    void fromMapWithNumber() {
        Map<String, Object> m = Map.of("n", 42);
        assertTrue(JsonWriter.fromMap(m).contains("\"n\":42"));
    }
}

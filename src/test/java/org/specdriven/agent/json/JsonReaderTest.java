package org.specdriven.agent.json;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonReaderTest {

    @Test
    void parseEmptyObject() {
        Map<String, Object> m = JsonReader.parseObject("{}");
        assertTrue(m.isEmpty());
    }

    @Test
    void parseStringField() {
        Map<String, Object> m = JsonReader.parseObject("{\"key\":\"value\"}");
        assertEquals("value", m.get("key"));
    }

    @Test
    void parseLongField() {
        Map<String, Object> m = JsonReader.parseObject("{\"n\":42}");
        assertEquals(42L, ((Number) m.get("n")).longValue());
    }

    @Test
    void parseDoubleField() {
        Map<String, Object> m = JsonReader.parseObject("{\"t\":0.7}");
        assertEquals(0.7, ((Number) m.get("t")).doubleValue(), 1e-9);
    }

    @Test
    void parseBooleanField() {
        Map<String, Object> m = JsonReader.parseObject("{\"ok\":true,\"fail\":false}");
        assertEquals(Boolean.TRUE, m.get("ok"));
        assertEquals(Boolean.FALSE, m.get("fail"));
    }

    @Test
    void parseNullField() {
        Map<String, Object> m = JsonReader.parseObject("{\"x\":null}");
        assertTrue(m.containsKey("x"));
        assertNull(m.get("x"));
    }

    @Test
    void parseNestedObject() {
        Map<String, Object> m = JsonReader.parseObject("{\"a\":{\"b\":\"c\"}}");
        @SuppressWarnings("unchecked")
        Map<String, Object> inner = (Map<String, Object>) m.get("a");
        assertEquals("c", inner.get("b"));
    }

    @Test
    void parseArray() {
        Map<String, Object> m = JsonReader.parseObject("{\"arr\":[1,2,3]}");
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) m.get("arr");
        assertEquals(3, list.size());
    }

    @Test
    void parseStringEscapes() {
        Map<String, Object> m = JsonReader.parseObject("{\"s\":\"say \\\"hi\\\"\\nnewline\"}");
        assertEquals("say \"hi\"\nnewline", m.get("s"));
    }

    @Test
    void getStringDotPath() {
        Map<String, Object> root = JsonReader.parseObject("{\"a\":{\"b\":\"hello\"}}");
        assertEquals("hello", JsonReader.getString(root, "a.b"));
    }

    @Test
    void getStringArrayIndex() {
        Map<String, Object> root = JsonReader.parseObject("{\"choices\":[{\"content\":\"x\"}]}");
        assertEquals("x", JsonReader.getString(root, "choices.0.content"));
    }

    @Test
    void getStringMissingPath() {
        Map<String, Object> root = JsonReader.parseObject("{}");
        assertNull(JsonReader.getString(root, "a.b.c"));
    }

    @Test
    void getLong() {
        Map<String, Object> root = JsonReader.parseObject("{\"usage\":{\"prompt_tokens\":10}}");
        assertEquals(10L, JsonReader.getLong(root, "usage.prompt_tokens"));
    }

    @Test
    void getLongMissing() {
        assertEquals(0L, JsonReader.getLong(Map.of(), "missing"));
    }

    @Test
    void getList() {
        Map<String, Object> root = JsonReader.parseObject("{\"items\":[\"a\",\"b\"]}");
        List<Object> list = JsonReader.getList(root, "items");
        assertEquals(2, list.size());
        assertEquals("a", list.get(0));
    }

    @Test
    void getListMissing() {
        assertTrue(JsonReader.getList(Map.of(), "nope").isEmpty());
    }

    @Test
    void getMap() {
        Map<String, Object> root = JsonReader.parseObject("{\"inner\":{\"k\":\"v\"}}");
        Map<String, Object> inner = JsonReader.getMap(root, "inner");
        assertEquals("v", inner.get("k"));
    }

    @Test
    void getMapMissing() {
        assertTrue(JsonReader.getMap(Map.of(), "nope").isEmpty());
    }

    @Test
    void parseObjectWrappedInWhitespace() {
        Map<String, Object> m = JsonReader.parseObject("  { \"k\" : \"v\" }  ");
        assertEquals("v", m.get("k"));
    }

    @Test
    void parseNegativeNumber() {
        Map<String, Object> m = JsonReader.parseObject("{\"n\":-5}");
        assertEquals(-5L, ((Number) m.get("n")).longValue());
    }
}

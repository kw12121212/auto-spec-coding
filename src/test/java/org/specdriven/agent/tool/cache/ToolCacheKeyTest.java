package org.specdriven.agent.tool.cache;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolCacheKeyTest {

    @Test
    void sameInputs_produceSameKey() {
        Map<String, Object> params = Map.of("path", "/foo.txt", "limit", 10);
        String key1 = ToolCacheKey.generate("read", params);
        String key2 = ToolCacheKey.generate("read", params);
        assertEquals(key1, key2);
    }

    @Test
    void differentToolNames_produceDifferentKeys() {
        Map<String, Object> params = Map.of("path", "/foo.txt");
        String key1 = ToolCacheKey.generate("read", params);
        String key2 = ToolCacheKey.generate("grep", params);
        assertNotEquals(key1, key2);
    }

    @Test
    void differentParams_produceDifferentKeys() {
        String key1 = ToolCacheKey.generate("read", Map.of("path", "/foo.txt"));
        String key2 = ToolCacheKey.generate("read", Map.of("path", "/bar.txt"));
        assertNotEquals(key1, key2);
    }

    @Test
    void keyIs64CharHex() {
        String key = ToolCacheKey.generate("read", Map.of("path", "/foo.txt"));
        assertEquals(64, key.length());
        assertTrue(key.matches("[0-9a-f]{64}"));
    }

    @Test
    void parameterOrderIndependence() {
        // LinkedHashMap preserves insertion order
        Map<String, Object> params1 = new LinkedHashMap<>();
        params1.put("a", "1");
        params1.put("b", "2");

        Map<String, Object> params2 = new LinkedHashMap<>();
        params2.put("b", "2");
        params2.put("a", "1");

        String key1 = ToolCacheKey.generate("read", params1);
        String key2 = ToolCacheKey.generate("read", params2);
        assertEquals(key1, key2);
    }
}

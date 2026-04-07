package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmConfigTest {

    @Test
    void fromMap_withAllKeys() {
        Map<String, String> map = Map.of(
                "baseUrl", "https://api.example.com",
                "apiKey", "sk-test-key",
                "model", "gpt-4o",
                "timeout", "30",
                "maxRetries", "5"
        );
        LlmConfig config = LlmConfig.fromMap(map);
        assertEquals("https://api.example.com", config.baseUrl());
        assertEquals("sk-test-key", config.apiKey());
        assertEquals("gpt-4o", config.model());
        assertEquals(30, config.timeout());
        assertEquals(5, config.maxRetries());
    }

    @Test
    void fromMap_missingKeys_useDefaults() {
        Map<String, String> map = Map.of(
                "apiKey", "sk-test-key"
        );
        LlmConfig config = LlmConfig.fromMap(map);
        assertEquals("https://api.openai.com/v1", config.baseUrl()); // default
        assertEquals("gpt-4", config.model()); // default
        assertEquals(60, config.timeout()); // default
        assertEquals(3, config.maxRetries()); // default
    }

    @Test
    void fromMap_nullMap_throws() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> LlmConfig.fromMap(null)
        );
        assertTrue(ex.getMessage().contains("null"));
    }

    @Test
    void fromMap_missingApiKey_throws() {
        Map<String, String> map = Map.of("baseUrl", "https://example.com");
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> LlmConfig.fromMap(map)
        );
        assertTrue(ex.getMessage().contains("apiKey"));
    }

    @Test
    void fromMap_invalidTimeout_usesDefault() {
        Map<String, String> map = Map.of(
                "apiKey", "sk-test-key",
                "timeout", "not-a-number"
        );
        LlmConfig config = LlmConfig.fromMap(map);
        assertEquals(60, config.timeout()); // falls back to default
    }

    @Test
    void constructor_blankBaseUrl_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new LlmConfig("", "key", "model", 30, 3));
    }

    @Test
    void constructor_negativeTimeout_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new LlmConfig("https://example.com", "key", "model", -1, 3));
    }

    @Test
    void constructor_negativeMaxRetries_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new LlmConfig("https://example.com", "key", "model", 30, -1));
    }
}

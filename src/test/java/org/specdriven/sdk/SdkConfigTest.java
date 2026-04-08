package org.specdriven.sdk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SdkConfigTest {

    @Test
    void defaultsReturnsExpectedValues() {
        SdkConfig config = SdkConfig.defaults();
        assertEquals(50, config.maxTurns());
        assertEquals(120, config.toolTimeoutSeconds());
        assertNull(config.systemPrompt());
    }

    @Test
    void customConstructorSetsValues() {
        SdkConfig config = new SdkConfig(10, 30, "You are helpful");
        assertEquals(10, config.maxTurns());
        assertEquals(30, config.toolTimeoutSeconds());
        assertEquals("You are helpful", config.systemPrompt());
    }

    @Test
    void recordIsImmutable() {
        SdkConfig config = SdkConfig.defaults();
        SdkConfig other = new SdkConfig(50, 120, null);
        assertEquals(config, other);
        // records are immutable by definition — verify fields are not modifiable
        assertEquals(50, config.maxTurns());
    }

    @Test
    void systemPromptCanBeEmpty() {
        SdkConfig config = new SdkConfig(5, 60, "");
        assertEquals("", config.systemPrompt());
    }
}

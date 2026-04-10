package org.specdriven.agent.answer;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AnswerAgentConfigTest {

    @Test
    void testDefaultValues() {
        AnswerAgentConfig config = AnswerAgentConfig.openAiMiniDefaults();

        assertEquals("openai", config.providerName());
        assertEquals("gpt-4o-mini", config.model());
        assertEquals(0.3, config.temperature());
        assertEquals(1024, config.maxTokens());
        assertEquals(30, config.timeoutSeconds());
        assertEquals(10, config.maxContextMessages());
    }

    @Test
    void testCustomValues() {
        AnswerAgentConfig config = new AnswerAgentConfig(
                "claude",
                "claude-3-haiku",
                0.5,
                2048,
                60,
                20
        );

        assertEquals("claude", config.providerName());
        assertEquals("claude-3-haiku", config.model());
        assertEquals(0.5, config.temperature());
        assertEquals(2048, config.maxTokens());
        assertEquals(60, config.timeoutSeconds());
        assertEquals(20, config.maxContextMessages());
    }

    @Test
    void testTwoArgConstructor() {
        AnswerAgentConfig config = new AnswerAgentConfig("openai", "gpt-4o");

        assertEquals("openai", config.providerName());
        assertEquals("gpt-4o", config.model());
        assertEquals(0.3, config.temperature());
        assertEquals(1024, config.maxTokens());
    }

    @Test
    void testNullProviderNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new AnswerAgentConfig(null, "gpt-4o")
        );
    }

    @Test
    void testBlankProviderNameThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new AnswerAgentConfig("  ", "gpt-4o")
        );
    }

    @Test
    void testNullModelThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new AnswerAgentConfig("openai", null)
        );
    }

    @Test
    void testBlankModelThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new AnswerAgentConfig("openai", "  ")
        );
    }

    @Test
    void testNegativeTemperatureThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new AnswerAgentConfig("openai", "gpt-4o", -0.1, 1024, 30, 10)
        );
    }

    @Test
    void testTemperatureAboveMaxThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new AnswerAgentConfig("openai", "gpt-4o", 2.1, 1024, 30, 10)
        );
    }

    @Test
    void testZeroMaxTokensThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new AnswerAgentConfig("openai", "gpt-4o", 0.3, 0, 30, 10)
        );
    }

    @Test
    void testNegativeMaxTokensThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new AnswerAgentConfig("openai", "gpt-4o", 0.3, -1, 30, 10)
        );
    }

    @Test
    void testZeroTimeoutThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new AnswerAgentConfig("openai", "gpt-4o", 0.3, 1024, 0, 10)
        );
    }

    @Test
    void testNegativeTimeoutThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new AnswerAgentConfig("openai", "gpt-4o", 0.3, 1024, -1, 10)
        );
    }

    @Test
    void testZeroMaxContextMessagesThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new AnswerAgentConfig("openai", "gpt-4o", 0.3, 1024, 30, 0)
        );
    }

    @Test
    void testNegativeMaxContextMessagesThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                new AnswerAgentConfig("openai", "gpt-4o", 0.3, 1024, 30, -1)
        );
    }
}

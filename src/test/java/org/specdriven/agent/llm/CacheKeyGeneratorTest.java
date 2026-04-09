package org.specdriven.agent.llm;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.agent.*;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CacheKeyGeneratorTest {

    @Test
    void sameRequestProducesSameKey() {
        LlmRequest r1 = new LlmRequest(
                List.of(new UserMessage("hello", 1)), "system", null, 0.7, 4096, null);
        LlmRequest r2 = new LlmRequest(
                List.of(new UserMessage("hello", 2)), "system", null, 0.7, 4096, null);

        String k1 = CacheKeyGenerator.generate(r1);
        String k2 = CacheKeyGenerator.generate(r2);

        assertEquals(k1, k2);
    }

    @Test
    void differentMessagesProduceDifferentKeys() {
        LlmRequest r1 = new LlmRequest(
                List.of(new UserMessage("hello", 1)), null, null, 0.7, 4096, null);
        LlmRequest r2 = new LlmRequest(
                List.of(new UserMessage("world", 1)), null, null, 0.7, 4096, null);

        String k1 = CacheKeyGenerator.generate(r1);
        String k2 = CacheKeyGenerator.generate(r2);

        assertNotEquals(k1, k2);
    }

    @Test
    void keyIs64CharHexString() {
        LlmRequest request = LlmRequest.of(List.of(new UserMessage("test", 1)));
        String key = CacheKeyGenerator.generate(request);

        assertEquals(64, key.length());
        assertTrue(key.matches("[0-9a-f]{64}"));
    }

    @Test
    void nullToolsProduceStableKey() {
        LlmRequest r1 = new LlmRequest(
                List.of(new UserMessage("hello", 1)), null, null, 0.7, 4096, null);
        LlmRequest r2 = new LlmRequest(
                List.of(new UserMessage("hello", 1)), null, List.of(), 0.7, 4096, null);

        // null tools and empty tools list should produce the same key
        assertEquals(CacheKeyGenerator.generate(r1), CacheKeyGenerator.generate(r2));
    }

    @Test
    void differentTemperatureProducesDifferentKey() {
        LlmRequest r1 = new LlmRequest(
                List.of(new UserMessage("hello", 1)), null, null, 0.7, 4096, null);
        LlmRequest r2 = new LlmRequest(
                List.of(new UserMessage("hello", 1)), null, null, 0.5, 4096, null);

        assertNotEquals(CacheKeyGenerator.generate(r1), CacheKeyGenerator.generate(r2));
    }

    @Test
    void differentToolsProduceDifferentKey() {
        ToolSchema tool1 = new ToolSchema("bash", "run shell", Map.of("type", "object"));
        ToolSchema tool2 = new ToolSchema("grep", "search content", Map.of("type", "object"));

        LlmRequest r1 = new LlmRequest(
                List.of(new UserMessage("hello", 1)), null, List.of(tool1), 0.7, 4096, null);
        LlmRequest r2 = new LlmRequest(
                List.of(new UserMessage("hello", 1)), null, List.of(tool2), 0.7, 4096, null);

        assertNotEquals(CacheKeyGenerator.generate(r1), CacheKeyGenerator.generate(r2));
    }
}

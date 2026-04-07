package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmRequestTest {

    private static final List<Message> MESSAGES = List.of(
            new UserMessage("hello", System.currentTimeMillis())
    );

    @Test
    void of_minimalRequest() {
        LlmRequest request = LlmRequest.of(MESSAGES);
        assertEquals(MESSAGES, request.messages());
        assertNull(request.systemPrompt());
        assertTrue(request.tools().isEmpty());
        assertEquals(0.7, request.temperature());
        assertEquals(4096, request.maxTokens());
        assertTrue(request.extra().isEmpty());
    }

    @Test
    void of_withSystemPrompt() {
        LlmRequest request = LlmRequest.of(MESSAGES, "You are helpful");
        assertEquals("You are helpful", request.systemPrompt());
    }

    @Test
    void constructor_fullRequest() {
        ToolSchema tool = new ToolSchema("test", "desc", Map.of());
        LlmRequest request = new LlmRequest(
                MESSAGES, "system", List.of(tool), 0.5, 100, Map.of("key", "val")
        );
        assertEquals("system", request.systemPrompt());
        assertEquals(1, request.tools().size());
        assertEquals(0.5, request.temperature());
        assertEquals(100, request.maxTokens());
        assertEquals("val", request.extra().get("key"));
    }

    @Test
    void constructor_nullMessages_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new LlmRequest(null, null, null, 0.7, 100, null));
    }

    @Test
    void constructor_emptyMessages_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new LlmRequest(List.of(), null, null, 0.7, 100, null));
    }

    @Test
    void constructor_invalidTemperature_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new LlmRequest(MESSAGES, null, null, -0.1, 100, null));
        assertThrows(IllegalArgumentException.class,
                () -> new LlmRequest(MESSAGES, null, null, 2.1, 100, null));
    }

    @Test
    void constructor_invalidMaxTokens_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new LlmRequest(MESSAGES, null, null, 0.7, 0, null));
    }

    @Test
    void constructor_nullTools_defaultsToEmpty() {
        LlmRequest request = new LlmRequest(MESSAGES, null, null, 0.7, 100, null);
        assertTrue(request.tools().isEmpty());
    }

    @Test
    void messagesIsImmutable() {
        LlmRequest request = LlmRequest.of(new java.util.ArrayList<>(MESSAGES));
        List<Message> retrieved = request.messages();
        assertThrows(UnsupportedOperationException.class,
                () -> retrieved.add(new UserMessage("extra", System.currentTimeMillis())));
    }
}

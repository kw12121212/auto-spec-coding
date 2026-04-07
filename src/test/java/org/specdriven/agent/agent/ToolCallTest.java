package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallTest {

    @Test
    void storesFields() {
        ToolCall call = new ToolCall("bash", Map.of("command", "ls"));
        assertEquals("bash", call.toolName());
        assertEquals(Map.of("command", "ls"), call.parameters());
    }

    @Test
    void defensivelyCopiesParameters() {
        Map<String, Object> params = new java.util.HashMap<>(Map.of("key", "val"));
        ToolCall call = new ToolCall("tool", params);
        params.put("extra", "should not appear");
        assertFalse(call.parameters().containsKey("extra"));
    }

    @Test
    void nullParametersBecomesEmptyMap() {
        ToolCall call = new ToolCall("tool", null);
        assertTrue(call.parameters().isEmpty());
    }

    @Test
    void parametersAreImmutable() {
        ToolCall call = new ToolCall("tool", Map.of("a", 1));
        assertThrows(UnsupportedOperationException.class, () ->
                call.parameters().put("b", 2));
    }
}

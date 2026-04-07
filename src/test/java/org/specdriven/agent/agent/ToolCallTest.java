package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallTest {

    @Test
    void storesFields() {
        ToolCall call = new ToolCall("bash", Map.of("command", "ls"), "call_123");
        assertEquals("bash", call.toolName());
        assertEquals(Map.of("command", "ls"), call.parameters());
        assertEquals("call_123", call.callId());
    }

    @Test
    void defensivelyCopiesParameters() {
        Map<String, Object> params = new java.util.HashMap<>(Map.of("key", "val"));
        ToolCall call = new ToolCall("tool", params, null);
        params.put("extra", "should not appear");
        assertFalse(call.parameters().containsKey("extra"));
    }

    @Test
    void nullParametersBecomesEmptyMap() {
        ToolCall call = new ToolCall("tool", null, null);
        assertTrue(call.parameters().isEmpty());
    }

    @Test
    void parametersAreImmutable() {
        ToolCall call = new ToolCall("tool", Map.of("a", 1), null);
        assertThrows(UnsupportedOperationException.class, () ->
                call.parameters().put("b", 2));
    }
}

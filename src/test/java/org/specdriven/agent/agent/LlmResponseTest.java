package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmResponseTest {

    @Test
    void textResponse_backwardCompatible() {
        LlmResponse.TextResponse resp = new LlmResponse.TextResponse("hello");
        assertEquals("hello", resp.content());
        assertNull(resp.usage());
        assertEquals("stop", resp.finishReason());
    }

    @Test
    void textResponse_full() {
        LlmUsage usage = new LlmUsage(10, 20, 30);
        LlmResponse.TextResponse resp = new LlmResponse.TextResponse("hello", usage, "stop");
        assertEquals("hello", resp.content());
        assertEquals(10, resp.usage().promptTokens());
        assertEquals(20, resp.usage().completionTokens());
        assertEquals(30, resp.usage().totalTokens());
        assertEquals("stop", resp.finishReason());
    }

    @Test
    void toolCallResponse_backwardCompatible() {
        List<ToolCall> calls = List.of(new ToolCall("bash", Map.of("command", "ls"), null));
        LlmResponse.ToolCallResponse resp = new LlmResponse.ToolCallResponse(calls);
        assertEquals(1, resp.toolCalls().size());
        assertEquals("bash", resp.toolCalls().get(0).toolName());
        assertNull(resp.usage());
        assertEquals("tool_calls", resp.finishReason());
    }

    @Test
    void toolCallResponse_full() {
        List<ToolCall> calls = List.of(new ToolCall("read", Map.of("path", "/tmp"), null));
        LlmUsage usage = new LlmUsage(15, 25, 40);
        LlmResponse.ToolCallResponse resp = new LlmResponse.ToolCallResponse(calls, usage, "tool_calls");
        assertEquals(1, resp.toolCalls().size());
        assertEquals(15, resp.usage().promptTokens());
        assertEquals("tool_calls", resp.finishReason());
    }

    @Test
    void toolCallResponse_nullToolCalls_defaultsToEmpty() {
        LlmResponse.ToolCallResponse resp = new LlmResponse.ToolCallResponse(null);
        assertTrue(resp.toolCalls().isEmpty());
    }

    @Test
    void toolCallResponse_defensivelyCopiesList() {
        java.util.List<ToolCall> calls = new java.util.ArrayList<>();
        calls.add(new ToolCall("bash", Map.of(), null));
        LlmResponse.ToolCallResponse resp = new LlmResponse.ToolCallResponse(calls);
        calls.add(new ToolCall("grep", Map.of(), null));
        assertEquals(1, resp.toolCalls().size());
    }

    @Test
    void textResponse_nullContent_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new LlmResponse.TextResponse(null));
    }

    @Test
    void sealedInterfacePatternMatch() {
        LlmResponse text = new LlmResponse.TextResponse("hi");
        LlmResponse tools = new LlmResponse.ToolCallResponse(List.of());

        assertTrue(text instanceof LlmResponse.TextResponse);
        assertTrue(tools instanceof LlmResponse.ToolCallResponse);
    }

    @Test
    void usage_negativeTokens_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new LlmUsage(-1, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new LlmUsage(0, -1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new LlmUsage(0, 0, -1));
    }
}

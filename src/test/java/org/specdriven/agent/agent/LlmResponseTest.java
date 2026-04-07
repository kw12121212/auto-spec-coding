package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LlmResponseTest {

    @Test
    void textResponseCarriesContent() {
        LlmResponse.TextResponse resp = new LlmResponse.TextResponse("hello");
        assertEquals("hello", resp.content());
    }

    @Test
    void toolCallResponseCarriesCalls() {
        ToolCall call = new ToolCall("bash", Map.of("command", "ls"));
        LlmResponse.ToolCallResponse resp = new LlmResponse.ToolCallResponse(List.of(call));
        assertEquals(1, resp.toolCalls().size());
        assertEquals("bash", resp.toolCalls().get(0).toolName());
    }

    @Test
    void toolCallResponseDefensivelyCopiesList() {
        java.util.List<ToolCall> calls = new java.util.ArrayList<>();
        calls.add(new ToolCall("bash", Map.of()));
        LlmResponse.ToolCallResponse resp = new LlmResponse.ToolCallResponse(calls);
        calls.add(new ToolCall("grep", Map.of()));
        assertEquals(1, resp.toolCalls().size());
    }

    @Test
    void sealedInterfacePatternMatch() {
        LlmResponse text = new LlmResponse.TextResponse("hi");
        LlmResponse tools = new LlmResponse.ToolCallResponse(List.of());

        assertTrue(text instanceof LlmResponse.TextResponse);
        assertTrue(tools instanceof LlmResponse.ToolCallResponse);
    }
}

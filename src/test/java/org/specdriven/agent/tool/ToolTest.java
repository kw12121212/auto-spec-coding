package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class ToolTest {

    @Test
    void anonymousToolExecutesAndReturnsResult() {
        Tool tool = new Tool() {
            @Override
            public String getName() { return "echo"; }

            @Override
            public String getDescription() { return "Echoes input back"; }

            @Override
            public List<ToolParameter> getParameters() {
                return List.of(new ToolParameter("message", "string", "Message to echo", true));
            }

            @Override
            public ToolResult execute(ToolInput input, ToolContext context) {
                String msg = (String) input.parameters().get("message");
                return new ToolResult.Success(msg);
            }
        };

        assertEquals("echo", tool.getName());
        assertEquals("Echoes input back", tool.getDescription());
        assertEquals(1, tool.getParameters().size());

        ToolInput input = new ToolInput(Map.of("message", "hello"));
        ToolResult result = tool.execute(input, null);
        assertTrue(result instanceof ToolResult.Success);
        assertEquals("hello", ((ToolResult.Success) result).output());
    }
}

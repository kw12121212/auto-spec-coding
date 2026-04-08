package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class BackgroundToolTest {

    /**
     * Stub BackgroundTool for testing the interface contract.
     */
    static class StubBackgroundTool implements BackgroundTool {

        private final String name;

        StubBackgroundTool(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "stub background tool";
        }

        @Override
        public List<ToolParameter> getParameters() {
            return List.of();
        }

        @Override
        public ToolResult startBackground(ToolInput input, ToolContext context) {
            BackgroundProcessHandle handle = new BackgroundProcessHandle(
                    1234, "test-command", name, System.currentTimeMillis(), ProcessState.RUNNING);
            String json = "{\"id\":\"" + handle.id() + "\",\"pid\":1234,\"command\":\"test-command\"," +
                    "\"toolName\":\"" + name + "\",\"startTime\":" + handle.startTime() + ",\"state\":\"RUNNING\"}";
            return new ToolResult.Success(json);
        }
    }

    @Test
    void executeDelegatesToStartBackground() {
        BackgroundTool tool = new StubBackgroundTool("test-bg");
        ToolInput input = ToolInput.empty();
        ToolResult result = tool.execute(input, null);

        assertTrue(result instanceof ToolResult.Success);
        String output = ((ToolResult.Success) result).output();
        assertTrue(output.contains("test-command"));
        assertTrue(output.contains("\"state\":\"RUNNING\""));
    }

    @Test
    void backgroundToolExtendsTool() {
        BackgroundTool tool = new StubBackgroundTool("bg");
        assertInstanceOf(Tool.class, tool);
    }

    @Test
    void startBackgroundReturnsToolResult() {
        BackgroundTool tool = new StubBackgroundTool("bg2");
        ToolResult result = tool.startBackground(ToolInput.empty(), null);
        assertNotNull(result);
        assertTrue(result instanceof ToolResult.Success);
    }
}

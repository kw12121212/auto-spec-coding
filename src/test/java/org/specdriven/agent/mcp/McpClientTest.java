package org.specdriven.agent.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.testsupport.MockMcpServerMain;
import org.specdriven.agent.testsupport.SubprocessTestCommand;

class McpClientTest {

    @Test
    void initialize_completesAndSetsInitialized() throws Exception {
        try (McpClient client = new McpClient(command("standard"), 10)) {
            assertFalse(client.isInitialized());
            client.initialize();
            assertTrue(client.isInitialized());
        }
    }

    @Test
    void initialize_validatesProtocolVersion() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> {
            try (McpClient client = new McpClient(command("bad-version"), 10)) {
                client.initialize();
            }
        });
        assertTrue(ex.getMessage().contains("Unsupported"));
    }

    @Test
    void toolsList_returnsToolDescriptors() throws Exception {
        try (McpClient client = new McpClient(command("standard"), 10)) {
            client.initialize();
            List<Map<String, Object>> tools = client.toolsList();
            assertEquals(2, tools.size());

            Map<String, Object> tool1 = tools.get(0);
            assertEquals("read_file", tool1.get("name"));
            assertEquals("Read a file", tool1.get("description"));
            assertNotNull(tool1.get("inputSchema"));
        }
    }

    @Test
    void callTool_returnsSuccessResult() throws Exception {
        try (McpClient client = new McpClient(command("standard"), 10)) {
            client.initialize();

            McpClient.McpToolResult result = client.callTool("read_file", Map.of("path", "/tmp/test.txt"));
            assertFalse(result.isError());
            assertEquals("file content here", result.extractText());
        }
    }

    @Test
    void callTool_returnsErrorResult() throws Exception {
        try (McpClient client = new McpClient(command("error"), 10)) {
            client.initialize();

            McpClient.McpToolResult result = client.callTool("failing_tool", Map.of());
            assertTrue(result.isError());
        }
    }

    @Test
    void callTool_timeoutThrowsException() throws Exception {
        try (McpClient client = new McpClient(command("slow"), 2)) {
            client.initialize();
            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> client.callTool("slow_tool", Map.of()));
            assertTrue(ex.getMessage().contains("timed out"));
        }
    }

    @Test
    void close_terminatesProcess() throws Exception {
        McpClient client = new McpClient(command("standard"), 10);
        client.initialize();
        client.close();
    }

    @Test
    void invalidCommand_throwsOnInitialize() {
        assertThrows(Exception.class, () -> {
            try (McpClient client = new McpClient("/nonexistent/command", 5)) {
                client.initialize();
            }
        });
    }

    private static String command(String mode) {
        return SubprocessTestCommand.shellSafeJavaCommand(MockMcpServerMain.class, mode);
    }
}

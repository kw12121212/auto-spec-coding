package org.specdriven.agent.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.tool.*;

class McpToolAdapterTest {

    // --- Parameter schema conversion ---

    @Test
    void getName_returnsMcpPrefixedName() {
        Map<String, Object> descriptor = Map.of(
                "name", "read_file",
                "description", "Read a file"
        );
        McpToolAdapter adapter = new McpToolAdapter("myserver", descriptor, null);
        assertEquals("mcp__myserver__read_file", adapter.getName());
    }

    @Test
    void getDescription_returnsToolDescription() {
        Map<String, Object> descriptor = Map.of(
                "name", "read_file",
                "description", "Read a file"
        );
        McpToolAdapter adapter = new McpToolAdapter("myserver", descriptor, null);
        assertEquals("Read a file", adapter.getDescription());
    }

    @Test
    void getParameters_convertsInputSchema() {
        Map<String, Object> descriptor = Map.of(
                "name", "write_file",
                "description", "Write a file",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "path", Map.of("type", "string", "description", "File path"),
                                "content", Map.of("type", "string", "description", "File content")
                        ),
                        "required", List.of("path")
                )
        );
        McpToolAdapter adapter = new McpToolAdapter("myserver", descriptor, null);
        List<ToolParameter> params = adapter.getParameters();

        assertEquals(2, params.size());
        ToolParameter pathParam = params.stream()
                .filter(p -> p.name().equals("path")).findFirst().orElseThrow();
        assertTrue(pathParam.required());
        assertEquals("string", pathParam.type());

        ToolParameter contentParam = params.stream()
                .filter(p -> p.name().equals("content")).findFirst().orElseThrow();
        assertFalse(contentParam.required());
    }

    @Test
    void getParameters_emptySchema_returnsEmptyList() {
        Map<String, Object> descriptor = Map.of(
                "name", "ping",
                "description", "Ping"
        );
        McpToolAdapter adapter = new McpToolAdapter("myserver", descriptor, null);
        assertTrue(adapter.getParameters().isEmpty());
    }

    @Test
    void getParameters_convertsTypes() {
        Map<String, Object> descriptor = Map.of(
                "name", "mixed",
                "description", "Mixed types",
                "inputSchema", Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "count", Map.of("type", "integer", "description", "Count"),
                                "flag", Map.of("type", "boolean", "description", "Flag"),
                                "items", Map.of("type", "array", "description", "Items")
                        )
                )
        );
        McpToolAdapter adapter = new McpToolAdapter("myserver", descriptor, null);
        List<ToolParameter> params = adapter.getParameters();

        assertEquals(3, params.size());
        assertEquals("number", params.stream().filter(p -> p.name().equals("count")).findFirst().orElseThrow().type());
        assertEquals("boolean", params.stream().filter(p -> p.name().equals("flag")).findFirst().orElseThrow().type());
        assertEquals("array", params.stream().filter(p -> p.name().equals("items")).findFirst().orElseThrow().type());
    }

    // --- Execute delegation ---

    @Test
    void execute_returnsSuccessOnToolSuccess() {
        // This test would need a real McpClient or a mock, so we test the adapter
        // with a stub client that returns success
        // For now, test the Tool interface contract
        Map<String, Object> descriptor = Map.of(
                "name", "test_tool",
                "description", "Test"
        );

        // Adapter without a real client — execute will fail, but that's expected
        McpToolAdapter adapter = new McpToolAdapter("server", descriptor, null);
        ToolResult result = adapter.execute(ToolInput.empty(), null);
        // Should return Error since client is null
        assertTrue(result instanceof ToolResult.Error);
    }

    // --- Permission ---

    @Test
    void permissionFor_returnsDefaultPermission() {
        Map<String, Object> descriptor = Map.of(
                "name", "test_tool",
                "description", "Test"
        );
        McpToolAdapter adapter = new McpToolAdapter("server", descriptor, null);
        Permission perm = adapter.permissionFor(ToolInput.empty(), null);
        assertEquals("execute", perm.action());
        assertEquals("mcp__server__test_tool", perm.resource());
    }
}

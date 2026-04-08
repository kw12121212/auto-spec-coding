package org.specdriven.agent.mcp;

import org.specdriven.agent.tool.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Adapts an MCP tool descriptor to the Tool interface.
 * Each discovered MCP tool becomes a first-class Tool instance.
 */
public class McpToolAdapter implements Tool {

    private final String serverName;
    private final String toolName;
    private final String description;
    private final List<ToolParameter> parameters;
    private final McpClient client;

    /**
     * @param serverName   the MCP server name (used for prefixing)
     * @param descriptor   the tool descriptor from tools/list (name, description, inputSchema)
     * @param client       the MCP client to delegate calls to
     */
    @SuppressWarnings("unchecked")
    public McpToolAdapter(String serverName, Map<String, Object> descriptor, McpClient client) {
        this.serverName = serverName;
        this.toolName = (String) descriptor.getOrDefault("name", "unknown");
        this.description = (String) descriptor.getOrDefault("description", "");
        this.client = client;

        // Convert inputSchema to ToolParameter list
        Map<String, Object> schema = (Map<String, Object>) descriptor.get("inputSchema");
        this.parameters = parseParameters(schema);
    }

    @Override
    public String getName() {
        return "mcp__" + serverName + "__" + toolName;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public List<ToolParameter> getParameters() {
        return parameters;
    }

    @Override
    @SuppressWarnings("unchecked")
    public ToolResult execute(ToolInput input, ToolContext context) {
        try {
            Map<String, Object> args = input.parameters();
            McpClient.McpToolResult result = client.callTool(toolName, args);

            String text = result.extractText();
            if (result.isError()) {
                return new ToolResult.Error(text.isEmpty() ? "MCP tool error" : text);
            }
            return new ToolResult.Success(text);

        } catch (Exception e) {
            return new ToolResult.Error("MCP tool call failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parse JSON Schema inputSchema into ToolParameter list.
     */
    @SuppressWarnings("unchecked")
    private static List<ToolParameter> parseParameters(Map<String, Object> schema) {
        if (schema == null) return List.of();

        Map<String, Object> properties = (Map<String, Object>) schema.get("properties");
        if (properties == null || properties.isEmpty()) return List.of();

        List<String> required = List.of();
        Object reqObj = schema.get("required");
        if (reqObj instanceof List<?> list) {
            required = list.stream().map(Object::toString).toList();
        }

        List<ToolParameter> params = new ArrayList<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            String name = entry.getKey();
            Map<String, Object> prop = (Map<String, Object>) entry.getValue();
            String type = schemaTypeToToolType((String) prop.getOrDefault("type", "string"));
            String desc = (String) prop.getOrDefault("description", "");
            boolean isRequired = required.contains(name);
            params.add(new ToolParameter(name, type, desc, isRequired));
        }
        return List.copyOf(params);
    }

    private static String schemaTypeToToolType(String jsonSchemaType) {
        if (jsonSchemaType == null) return "string";
        return switch (jsonSchemaType) {
            case "integer", "number" -> "number";
            case "boolean" -> "boolean";
            case "array" -> "array";
            case "object" -> "object";
            default -> "string";
        };
    }
}

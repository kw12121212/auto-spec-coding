package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolParameter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolSchemaTest {

    private static Tool makeTool(String name, String desc, List<ToolParameter> params) {
        return new Tool() {
            @Override public String getName() { return name; }
            @Override public String getDescription() { return desc; }
            @Override public List<ToolParameter> getParameters() { return params; }
            @Override public org.specdriven.agent.tool.ToolResult execute(
                    org.specdriven.agent.tool.ToolInput input,
                    org.specdriven.agent.tool.ToolContext context) {
                throw new UnsupportedOperationException("stub");
            }
        };
    }

    @Test
    void fromTool_withParameters() {
        Tool tool = makeTool("read_file", "Read a file from disk", List.of(
                new ToolParameter("path", "string", "File path to read", true),
                new ToolParameter("encoding", "string", "File encoding", false)
        ));

        ToolSchema schema = ToolSchema.from(tool);
        assertEquals("read_file", schema.name());
        assertEquals("Read a file from disk", schema.description());

        Map<String, Object> params = schema.parameters();
        assertEquals("object", params.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) params.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("path"));
        assertTrue(properties.containsKey("encoding"));

        @SuppressWarnings("unchecked")
        Map<String, Object> pathProp = (Map<String, Object>) properties.get("path");
        assertEquals("string", pathProp.get("type"));
        assertEquals("File path to read", pathProp.get("description"));

        @SuppressWarnings("unchecked")
        List<String> required = (List<String>) params.get("required");
        assertEquals(List.of("path"), required);
    }

    @Test
    void fromTool_emptyParameters() {
        Tool tool = makeTool("ping", "Check connectivity", List.of());
        ToolSchema schema = ToolSchema.from(tool);

        assertEquals("ping", schema.name());
        assertEquals("Check connectivity", schema.description());
        assertTrue(schema.parameters().isEmpty());
    }

    @Test
    void fromTool_nullParameters() {
        Tool tool = makeTool("echo", "Echo input", null);
        ToolSchema schema = ToolSchema.from(tool);
        assertTrue(schema.parameters().isEmpty());
    }

    @Test
    void fromTool_null_throws() {
        assertThrows(IllegalArgumentException.class, () -> ToolSchema.from(null));
    }

    @Test
    void constructor_blankName_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new ToolSchema("", "desc", Map.of()));
    }

    @Test
    void fromTool_typeMapping() {
        Tool tool = makeTool("tool", "desc", List.of(
                new ToolParameter("count", "integer", "Count", true),
                new ToolParameter("ratio", "double", "Ratio", false),
                new ToolParameter("flag", "boolean", "Flag", false),
                new ToolParameter("items", "array", "Items", false),
                new ToolParameter("data", "object", "Data", false)
        ));

        ToolSchema schema = ToolSchema.from(tool);

        @SuppressWarnings("unchecked")
        Map<String, Object> props = (Map<String, Object>) schema.parameters().get("properties");

        @SuppressWarnings("unchecked")
        Map<String, Object> countProp = (Map<String, Object>) props.get("count");
        assertEquals("integer", countProp.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> ratioProp = (Map<String, Object>) props.get("ratio");
        assertEquals("number", ratioProp.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> flagProp = (Map<String, Object>) props.get("flag");
        assertEquals("boolean", flagProp.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> itemsProp = (Map<String, Object>) props.get("items");
        assertEquals("array", itemsProp.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> dataProp = (Map<String, Object>) props.get("data");
        assertEquals("object", dataProp.get("type"));
    }
}

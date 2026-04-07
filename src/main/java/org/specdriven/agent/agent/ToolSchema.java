package org.specdriven.agent.agent;

import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolParameter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A tool definition in LLM-compatible format.
 Converts from the agent-layer {@link Tool} type to a format
 suitable for sending to LLM providers.
 */
public record ToolSchema(
        String name,
        String description,
        Map<String, Object> parameters
 ) {
    public ToolSchema {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be null or blank");
        }
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }

    /**
     * Creates a ToolSchema from an agent-layer {@link Tool} instance.
 Converts the tool's parameters into a JSON Schema format.
     *
     * @param tool the tool to convert (must not be null)
     * @return a ToolSchema with the tool's name, description(), and buildParametersSchema(tool.getParameters());
     */
    public static ToolSchema from(Tool tool) {
        if (tool == null) {
            throw new IllegalArgumentException("tool must not be null");
        }
        return new ToolSchema(tool.getName(), tool.getDescription(), buildParametersSchema(tool.getParameters()));
    }

    private static Map<String, Object> buildParametersSchema(List<ToolParameter> params) {
        if (params == null || params.isEmpty()) {
            return Map.of();
        }

        Map<String, Object> properties = new LinkedHashMap<>();
        for (ToolParameter param : params) {
            Map<String, Object> prop = new LinkedHashMap<>();
            prop.put("type", mapType(param.type()));
            prop.put("description", param.description());
            properties.put(param.name(), Collections.unmodifiableMap(prop));
        }
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", Collections.unmodifiableMap(properties));
        List<String> required = params.stream()
                .filter(ToolParameter::required)
                .map(ToolParameter::name)
                .toList();
        if (!required.isEmpty()) {
            schema.put("required", List.copyOf(required));
        }
        return Collections.unmodifiableMap(schema);
    }

    private static String mapType(String toolType) {
        if (toolType == null) return "string";
        return switch (toolType.toLowerCase()) {
            case "integer", "int", "long" -> "integer";
            case "number", "float", "double" -> "number";
            case "boolean", "bool" -> "boolean";
            case "array", "list" -> "array";
            case "object", "map" -> "object";
            default -> "string";
        };
    }
}

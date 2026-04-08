package org.specdriven.agent.http;

import java.util.List;
import java.util.Map;

/**
 * Metadata for a single tool.
 */
public record ToolInfo(
        String name,
        String description,
        List<Map<String, Object>> parameters) {

    public ToolInfo {
        if (parameters == null) {
            parameters = List.of();
        }
    }
}

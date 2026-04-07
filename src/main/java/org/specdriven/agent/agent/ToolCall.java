package org.specdriven.agent.agent;

import java.util.Collections;
import java.util.Map;

/**
 * Represents a tool invocation requested by the LLM.
 */
public record ToolCall(
        String toolName,
        Map<String, Object> parameters
) {
    public ToolCall {
        parameters = parameters != null ? Map.copyOf(parameters) : Collections.emptyMap();
    }
}

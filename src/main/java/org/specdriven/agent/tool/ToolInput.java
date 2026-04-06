package org.specdriven.agent.tool;

import java.util.Collections;
import java.util.Map;

/**
 * Wraps the input parameters passed to a tool on execution.
 *
 * @param parameters named parameter values
 */
public record ToolInput(
        Map<String, Object> parameters
) {
    public ToolInput {
        parameters = Map.copyOf(parameters);
    }

    /**
     * Returns an empty ToolInput with no parameters.
     */
    public static ToolInput empty() {
        return new ToolInput(Collections.emptyMap());
    }
}

package org.specdriven.agent.tool;

import java.util.List;
import java.util.Map;

import org.specdriven.agent.permission.Permission;

/**
 * Contract for an executable tool that an agent can invoke.
 */
public interface Tool {

    /**
     * Returns the unique name of this tool.
     */
    String getName();

    /**
     * Returns a human-readable description of what this tool does.
     */
    String getDescription();

    /**
     * Returns the parameters this tool accepts.
     */
    List<ToolParameter> getParameters();

    /**
     * Executes this tool with the given input and context.
     *
     * @param input   the tool input parameters
     * @param context the execution context
     * @return the result of execution
     */
    ToolResult execute(ToolInput input, ToolContext context);

    /**
     * Returns the Permission required to execute this tool with the given input.
     * Override to declare tool-specific permission semantics.
     *
     * @param input the tool input parameters
     * @param context the execution context (provides workDir for path resolution)
     * @return the permission describing the required access
     */
    default Permission permissionFor(ToolInput input, ToolContext context) {
        return new Permission("execute", getName(), Map.of());
    }
}

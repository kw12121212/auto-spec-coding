package org.specdriven.agent.tool;

import java.util.Map;
import org.specdriven.agent.permission.PermissionProvider;

/**
 * Execution context provided to a tool during execution.
 */
public interface ToolContext {

    /**
     * Returns the current working directory for tool execution.
     */
    String workDir();

    /**
     * Returns the permission provider for access checks.
     */
    PermissionProvider permissionProvider();

    /**
     * Returns environment variables available to the tool.
     * May return an empty map if no environment is provided.
     */
    Map<String, String> env();
}

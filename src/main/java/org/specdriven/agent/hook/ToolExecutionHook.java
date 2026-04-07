package org.specdriven.agent.hook;

import org.specdriven.agent.tool.Tool;
import org.specdriven.agent.tool.ToolContext;
import org.specdriven.agent.tool.ToolInput;
import org.specdriven.agent.tool.ToolResult;

/**
 * Hook that intercepts tool execution in the orchestrator.
 * Hooks run before and after each tool invocation.
 */
public interface ToolExecutionHook {

    /**
     * Called before a tool is executed.
     *
     * @return null to allow execution, or a ToolResult.Error to block it
     */
    ToolResult beforeExecute(Tool tool, ToolInput input, ToolContext context);

    /**
     * Called after a tool has been executed successfully.
     * Not called when beforeExecute blocked execution.
     */
    void afterExecute(Tool tool, ToolInput input, ToolResult result);
}

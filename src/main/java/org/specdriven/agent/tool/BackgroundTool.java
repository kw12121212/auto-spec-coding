package org.specdriven.agent.tool;

/**
 * A tool that launches a background process and returns immediately.
 * <p>
 * The synchronous {@link #execute(ToolInput, ToolContext)} method delegates to
 * {@link #startBackground(ToolInput, ToolContext)} and returns a
 * {@link ToolResult.Success} whose output is the JSON-serialized {@link BackgroundProcessHandle}.
 * <p>
 * Implementations MUST NOT block until process completion — they MUST return
 * immediately after launching the process.
 */
public interface BackgroundTool extends Tool {

    /**
     * Starts a background process with the given input and context.
     * Must return immediately after launching the process.
     *
     * @param input   the tool input parameters
     * @param context the execution context
     * @return ToolResult.Success with JSON-serialized BackgroundProcessHandle on success,
     *         or ToolResult.Error if the process fails to start
     */
    ToolResult startBackground(ToolInput input, ToolContext context);

    @Override
    default ToolResult execute(ToolInput input, ToolContext context) {
        return startBackground(input, context);
    }
}

package org.specdriven.agent.tool;

/**
 * Sealed result of a tool execution. Either {@link Success} or {@link Error}.
 */
public sealed interface ToolResult permits ToolResult.Success, ToolResult.Error {

    /**
     * Successful tool execution carrying output text.
     *
     * @param output the tool output
     */
    record Success(String output) implements ToolResult {}

    /**
     * Failed tool execution carrying an error message and optional cause.
     *
     * @param message error description
     * @param cause   optional throwable that caused the failure, may be null
     */
    record Error(String message, Throwable cause) implements ToolResult {
        public Error(String message) {
            this(message, null);
        }
    }
}

package org.specdriven.agent.tool.builtin;

/**
 * Exception thrown when a builtin tool cannot be resolved (not found locally and download failed).
 */
public class BuiltinToolException extends RuntimeException {

    public BuiltinToolException(String message) {
        super(message);
    }

    public BuiltinToolException(String message, Throwable cause) {
        super(message, cause);
    }
}

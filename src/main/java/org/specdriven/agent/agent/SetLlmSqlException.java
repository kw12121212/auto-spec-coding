package org.specdriven.agent.agent;

/**
 * Raised when a {@code SET LLM} SQL statement cannot be parsed or validated.
 */
public class SetLlmSqlException extends IllegalArgumentException {

    public SetLlmSqlException(String message) {
        super(message);
    }

    public SetLlmSqlException(String message, Throwable cause) {
        super(message, cause);
    }
}

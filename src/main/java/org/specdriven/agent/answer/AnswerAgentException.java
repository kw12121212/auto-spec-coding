package org.specdriven.agent.answer;

/**
 * Exception thrown when the Answer Agent encounters an error.
 */
public class AnswerAgentException extends RuntimeException {

    public AnswerAgentException(String message) {
        super(message);
    }

    public AnswerAgentException(String message, Throwable cause) {
        super(message, cause);
    }
}

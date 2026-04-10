package org.specdriven.agent.answer;

/**
 * Exception thrown when the Answer Agent times out waiting for an LLM response.
 */
public class AnswerAgentTimeoutException extends AnswerAgentException {

    private final long timeoutSeconds;

    public AnswerAgentTimeoutException(long timeoutSeconds) {
        super("Answer Agent timed out after " + timeoutSeconds + " seconds");
        this.timeoutSeconds = timeoutSeconds;
    }

    public AnswerAgentTimeoutException(long timeoutSeconds, Throwable cause) {
        super("Answer Agent timed out after " + timeoutSeconds + " seconds", cause);
        this.timeoutSeconds = timeoutSeconds;
    }

    /**
     * Returns the timeout duration in seconds.
     */
    public long timeoutSeconds() {
        return timeoutSeconds;
    }
}

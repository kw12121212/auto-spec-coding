package org.specdriven.agent.http;

/**
 * HTTP request to run an agent.
 */
public record RunAgentRequest(
        String prompt,
        String systemPrompt,
        Integer maxTurns,
        Integer toolTimeoutSeconds) {

    public RunAgentRequest {
        if (prompt == null) {
            throw new NullPointerException("prompt must not be null");
        }
    }
}

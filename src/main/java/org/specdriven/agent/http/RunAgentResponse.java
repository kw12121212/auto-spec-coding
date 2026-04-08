package org.specdriven.agent.http;

/**
 * Response from running an agent.
 */
public record RunAgentResponse(
        String agentId,
        String output,
        String state) {
}

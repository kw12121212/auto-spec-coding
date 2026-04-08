package org.specdriven.agent.http;

/**
 * Response from querying agent state.
 */
public record AgentStateResponse(
        String agentId,
        String state,
        long createdAt,
        long updatedAt) {
}

package org.specdriven.agent.http;

import java.util.Map;

/**
 * Payload sent to a remote tool callback when the backend executes the tool.
 */
public record RemoteToolInvocationRequest(
        String toolName,
        Map<String, Object> parameters
) {
    public RemoteToolInvocationRequest {
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
    }
}

package org.specdriven.agent.http;

import java.util.List;
import java.util.Map;

/**
 * Request for registering a callback-backed remote tool.
 */
public record RemoteToolRegistrationRequest(
        String name,
        String description,
        List<Map<String, Object>> parameters,
        String callbackUrl
) {
    public RemoteToolRegistrationRequest {
        parameters = parameters != null ? List.copyOf(parameters) : List.of();
    }
}

package org.specdriven.agent.http;

import java.util.Map;

/**
 * HTTP request to start a workflow instance.
 */
public record WorkflowStartRequest(
        String workflowName,
        Map<String, Object> input) {

    public WorkflowStartRequest {
        if (workflowName == null || workflowName.isBlank()) {
            throw new IllegalArgumentException("workflowName must not be blank");
        }
        input = input == null ? Map.of() : Map.copyOf(input);
    }
}

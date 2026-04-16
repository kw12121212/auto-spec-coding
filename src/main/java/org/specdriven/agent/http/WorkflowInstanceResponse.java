package org.specdriven.agent.http;

/**
 * HTTP response carrying workflow instance state.
 */
public record WorkflowInstanceResponse(
        String workflowId,
        String workflowName,
        String status,
        long createdAt,
        long updatedAt) {
}

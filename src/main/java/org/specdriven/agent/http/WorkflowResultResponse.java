package org.specdriven.agent.http;

/**
 * HTTP response carrying workflow result state.
 */
public record WorkflowResultResponse(
        String workflowId,
        String workflowName,
        String status,
        Object result,
        String failureSummary,
        long createdAt,
        long updatedAt) {
}

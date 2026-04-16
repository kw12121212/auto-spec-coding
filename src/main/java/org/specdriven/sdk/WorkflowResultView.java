package org.specdriven.sdk;

/**
 * Observable workflow result view returned by the SDK and transport layers.
 */
public record WorkflowResultView(
        String workflowId,
        String workflowName,
        WorkflowStatus status,
        Object result,
        String failureSummary,
        long createdAt,
        long updatedAt) {
}

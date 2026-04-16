package org.specdriven.sdk;

/**
 * Observable workflow instance state returned by the SDK and transport layers.
 */
public record WorkflowInstanceView(
        String workflowId,
        String workflowName,
        WorkflowStatus status,
        long createdAt,
        long updatedAt) {
}

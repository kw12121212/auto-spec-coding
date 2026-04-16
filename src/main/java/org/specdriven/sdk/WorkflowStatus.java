package org.specdriven.sdk;

/**
 * Observable workflow instance states for the first workflow runtime contract.
 */
public enum WorkflowStatus {
    ACCEPTED,
    RUNNING,
    WAITING_FOR_INPUT,
    SUCCEEDED,
    FAILED,
    CANCELLED
}

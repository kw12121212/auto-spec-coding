package org.specdriven.sdk;

import java.util.Map;

/**
 * The result of executing a single workflow step.
 */
public record WorkflowStepResult(Map<String, Object> output, String failureReason) {

    public WorkflowStepResult {
        output = (output == null) ? Map.of() : Map.copyOf(output);
    }

    public boolean isFailure() {
        return failureReason != null;
    }

    public static WorkflowStepResult success(Map<String, Object> output) {
        return new WorkflowStepResult(output, null);
    }

    public static WorkflowStepResult failure(String reason) {
        return new WorkflowStepResult(Map.of(), reason);
    }
}

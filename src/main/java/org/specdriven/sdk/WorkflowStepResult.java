package org.specdriven.sdk;

import java.util.Map;

/**
 * The result of executing a single workflow step.
 */
public record WorkflowStepResult(Map<String, Object> output, String failureReason, String inputPrompt) {

    public WorkflowStepResult {
        output = (output == null) ? Map.of() : Map.copyOf(output);
    }

    public boolean isFailure() {
        return failureReason != null;
    }

    public boolean isAwaitingInput() {
        return inputPrompt != null && failureReason == null;
    }

    public static WorkflowStepResult success(Map<String, Object> output) {
        return new WorkflowStepResult(output, null, null);
    }

    public static WorkflowStepResult failure(String reason) {
        return new WorkflowStepResult(Map.of(), reason, null);
    }

    public static WorkflowStepResult awaitingInput(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        return new WorkflowStepResult(Map.of(), null, prompt);
    }
}

package org.specdriven.sdk;

import java.util.Map;

/**
 * The result of executing a single workflow step.
 */
public record WorkflowStepResult(Map<String, Object> output, String failureReason, String inputPrompt,
                                 boolean retryableFailure) {

    public WorkflowStepResult {
        output = (output == null) ? Map.of() : Map.copyOf(output);
        if (inputPrompt != null && failureReason != null) {
            throw new IllegalArgumentException("failure result cannot also await input");
        }
        if (retryableFailure && failureReason == null) {
            throw new IllegalArgumentException("retryable failure requires a failure reason");
        }
    }

    public boolean isFailure() {
        return failureReason != null;
    }

    public boolean isAwaitingInput() {
        return inputPrompt != null && failureReason == null;
    }

    public boolean isRetryableFailure() {
        return retryableFailure;
    }

    public static WorkflowStepResult success(Map<String, Object> output) {
        return new WorkflowStepResult(output, null, null, false);
    }

    public static WorkflowStepResult failure(String reason) {
        return new WorkflowStepResult(Map.of(), reason, null, false);
    }

    public static WorkflowStepResult retryableFailure(String reason) {
        return new WorkflowStepResult(Map.of(), reason, null, true);
    }

    public static WorkflowStepResult awaitingInput(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("prompt must not be blank");
        }
        return new WorkflowStepResult(Map.of(), null, prompt, false);
    }
}

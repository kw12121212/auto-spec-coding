package org.specdriven.sdk;

/**
 * A step descriptor within a workflow declaration.
 */
public record WorkflowStep(StepType type, String name) {

    public WorkflowStep {
        if (type == null) {
            throw new IllegalArgumentException("step type must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("step name must not be blank");
        }
    }

    public enum StepType {
        SERVICE,
        TOOL,
        AGENT
    }
}

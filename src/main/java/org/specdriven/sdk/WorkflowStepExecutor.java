package org.specdriven.sdk;

import java.util.Map;

/**
 * Executes a single workflow step.
 */
public interface WorkflowStepExecutor {

    WorkflowStep.StepType stepType();

    WorkflowStepResult execute(WorkflowStep step, Map<String, Object> input);
}

package org.specdriven.agent.loop;

import org.specdriven.agent.event.EventBus;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Configuration for the autonomous loop driver.
 *
 * @param maxIterations          maximum number of loop iterations (must be positive)
 * @param iterationTimeoutSeconds timeout per iteration in seconds (must be positive)
 * @param targetMilestones       optional list of milestone file names to restrict scope;
 *                               empty means scan all milestones
 * @param projectRoot            root directory of the project (must not be null)
 * @param eventBus               event bus for publishing loop events (must not be null)
 */
public record LoopConfig(
        int maxIterations,
        int iterationTimeoutSeconds,
        List<String> targetMilestones,
        Path projectRoot,
        EventBus eventBus
) {
    public LoopConfig {
        if (projectRoot == null) throw new NullPointerException("projectRoot must not be null");
        if (eventBus == null) throw new NullPointerException("eventBus must not be null");
        if (maxIterations <= 0) throw new IllegalArgumentException("maxIterations must be positive");
        if (iterationTimeoutSeconds <= 0) throw new IllegalArgumentException("iterationTimeoutSeconds must be positive");
        targetMilestones = targetMilestones == null ? List.of() : Collections.unmodifiableList(List.copyOf(targetMilestones));
    }

    /**
     * Creates a LoopConfig with sensible defaults.
     */
    public static LoopConfig defaults(Path projectRoot, EventBus eventBus) {
        return new LoopConfig(10, 600, List.of(), projectRoot, eventBus);
    }
}

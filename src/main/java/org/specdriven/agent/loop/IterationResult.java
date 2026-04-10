package org.specdriven.agent.loop;

import java.util.Collections;
import java.util.List;

/**
 * Result of a single pipeline execution for one loop iteration.
 *
 * @param status          the outcome status
 * @param failureReason   reason for failure, null if successful
 * @param durationMs      wall-clock duration in milliseconds
 * @param phasesCompleted phases that completed successfully before termination
 */
public record IterationResult(
        IterationStatus status,
        String failureReason,
        long durationMs,
        List<PipelinePhase> phasesCompleted
) {
    public IterationResult {
        phasesCompleted = phasesCompleted == null
                ? List.of()
                : Collections.unmodifiableList(List.copyOf(phasesCompleted));
        if (durationMs < 0) throw new IllegalArgumentException("durationMs must be non-negative");
    }
}

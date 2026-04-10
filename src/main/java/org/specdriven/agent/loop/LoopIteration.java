package org.specdriven.agent.loop;

/**
 * Tracks a single loop iteration.
 *
 * @param iterationNumber the 1-based iteration number
 * @param changeName      name of the change being executed
 * @param milestoneFile   milestone file containing this change
 * @param startedAt       epoch millis when the iteration started
 * @param completedAt     epoch millis when completed, null if in progress
 * @param status          the outcome status
 * @param failureReason   reason for failure, null if successful
 */
public record LoopIteration(
        int iterationNumber,
        String changeName,
        String milestoneFile,
        long startedAt,
        Long completedAt,
        IterationStatus status,
        String failureReason
) {
    public LoopIteration {
        if (iterationNumber < 1) throw new IllegalArgumentException("iterationNumber must be >= 1");
    }
}

package org.specdriven.agent.loop;

import java.util.Collections;
import java.util.Set;

/**
 * Immutable snapshot of loop-level progress.
 *
 * @param loopState            current state of the loop
 * @param completedChangeNames names of changes that have completed
 * @param totalIterations      total number of iterations completed so far
 */
public record LoopProgress(
        LoopState loopState,
        Set<String> completedChangeNames,
        int totalIterations,
        long tokenUsage
) {
    public LoopProgress {
        completedChangeNames = completedChangeNames == null
                ? Set.of()
                : Collections.unmodifiableSet(Set.copyOf(completedChangeNames));
        if (totalIterations < 0) {
            throw new IllegalArgumentException("totalIterations must be non-negative");
        }
        if (tokenUsage < 0) {
            throw new IllegalArgumentException("tokenUsage must be non-negative");
        }
    }

    /**
     * Backward-compatible constructor without token usage (defaults to 0).
     */
    public LoopProgress(LoopState loopState, Set<String> completedChangeNames, int totalIterations) {
        this(loopState, completedChangeNames, totalIterations, 0);
    }
}

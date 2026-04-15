package org.specdriven.agent.loop;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable snapshot of loop-level progress.
 *
 * @param loopState            current state of the loop
 * @param completedChangeNames names of changes that have completed
 * @param totalIterations      total number of iterations completed so far
 * @param tokenUsage           cumulative token usage recovered across loop runs
 * @param activeCheckpoint     selected incomplete iteration checkpoint, if any
 */
public record LoopProgress(
        LoopState loopState,
        Set<String> completedChangeNames,
        int totalIterations,
        long tokenUsage,
        Optional<LoopPhaseCheckpoint> activeCheckpoint
) {
    public LoopProgress {
        completedChangeNames = completedChangeNames == null
                ? Set.of()
                : Collections.unmodifiableSet(Set.copyOf(completedChangeNames));
        activeCheckpoint = activeCheckpoint == null ? Optional.empty() : activeCheckpoint;
        if (totalIterations < 0) {
            throw new IllegalArgumentException("totalIterations must be non-negative");
        }
        if (tokenUsage < 0) {
            throw new IllegalArgumentException("tokenUsage must be non-negative");
        }
    }

    public LoopProgress(LoopState loopState, Set<String> completedChangeNames, int totalIterations,
                        long tokenUsage, LoopPhaseCheckpoint activeCheckpoint) {
        this(loopState, completedChangeNames, totalIterations, tokenUsage,
                Optional.ofNullable(activeCheckpoint));
    }

    /**
     * Backward-compatible constructor without active checkpoint.
     */
    public LoopProgress(LoopState loopState, Set<String> completedChangeNames, int totalIterations,
                        long tokenUsage) {
        this(loopState, completedChangeNames, totalIterations, tokenUsage, Optional.empty());
    }

    /**
     * Backward-compatible constructor without token usage (defaults to 0).
     */
    public LoopProgress(LoopState loopState, Set<String> completedChangeNames, int totalIterations) {
        this(loopState, completedChangeNames, totalIterations, 0, Optional.empty());
    }
}

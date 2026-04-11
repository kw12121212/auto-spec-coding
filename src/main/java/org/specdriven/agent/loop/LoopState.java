package org.specdriven.agent.loop;

import java.util.Map;
import java.util.Set;

/**
 * Lifecycle states of the autonomous loop driver.
 */
public enum LoopState {
    IDLE,
    RECOMMENDING,
    RUNNING,
    CHECKPOINT,
    QUESTIONING,
    PAUSED,
    STOPPED,
    ERROR;

    private static final Map<LoopState, Set<LoopState>> VALID_TRANSITIONS = Map.of(
            IDLE, Set.of(RECOMMENDING),
            RECOMMENDING, Set.of(RUNNING, PAUSED, STOPPED, ERROR),
            RUNNING, Set.of(CHECKPOINT, QUESTIONING, PAUSED, STOPPED, ERROR),
            CHECKPOINT, Set.of(RECOMMENDING, PAUSED, STOPPED, ERROR),
            QUESTIONING, Set.of(RUNNING, PAUSED, ERROR),
            PAUSED, Set.of(RECOMMENDING),
            ERROR, Set.of(IDLE)
    );

    /**
     * Validates that transitioning from this state to the target is allowed.
     *
     * @param target the desired next state
     * @throws IllegalStateException if the transition is not valid
     */
    public void requireTransitionTo(LoopState target) {
        Set<LoopState> allowed = VALID_TRANSITIONS.get(this);
        if (allowed == null || !allowed.contains(target)) {
            throw new IllegalStateException(
                    "Invalid state transition: " + this + " -> " + target);
        }
    }

    /**
     * Returns true if this state is STOPPED (terminal state).
     */
    public boolean isTerminal() {
        return this == STOPPED;
    }
}

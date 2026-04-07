package org.specdriven.agent.registry;

/**
 * Lifecycle states for a team in the registry.
 */
public enum TeamStatus {
    ACTIVE,
    DISSOLVED;

    /**
     * Validates that transitioning from {@code from} to {@code to} is allowed.
     *
     * @throws IllegalStateException if the transition is not valid
     */
    public static void validateTransition(TeamStatus from, TeamStatus to) {
        if (from == to) {
            throw new IllegalStateException("Cannot transition from " + from + " to " + to);
        }
        if (from == DISSOLVED) {
            throw new IllegalStateException("DISSOLVED is a terminal state — no transitions allowed");
        }
        if (from == ACTIVE && to == DISSOLVED) {
            return;
        }
        throw new IllegalStateException("Invalid transition: " + from + " -> " + to);
    }
}

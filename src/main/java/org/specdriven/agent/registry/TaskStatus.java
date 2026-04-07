package org.specdriven.agent.registry;

/**
 * Lifecycle states for a task in the registry.
 */
public enum TaskStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    DELETED;

    /**
     * Validates that transitioning from {@code from} to {@code to} is allowed.
     *
     * @throws IllegalStateException if the transition is not valid
     */
    public static void validateTransition(TaskStatus from, TaskStatus to) {
        if (from == to) {
            throw new IllegalStateException("Cannot transition from " + from + " to " + to);
        }
        if (from == COMPLETED) {
            throw new IllegalStateException("COMPLETED is a terminal state — no transitions allowed");
        }
        if (from == DELETED) {
            throw new IllegalStateException("DELETED is a terminal state — no transitions allowed");
        }
        boolean valid = switch (from) {
            case PENDING -> to == IN_PROGRESS || to == DELETED;
            case IN_PROGRESS -> to == COMPLETED || to == DELETED;
            default -> false;
        };
        if (!valid) {
            throw new IllegalStateException("Invalid transition: " + from + " -> " + to);
        }
    }
}

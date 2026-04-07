package org.specdriven.agent.registry;

/**
 * Lifecycle states for a cron entry in the registry.
 */
public enum CronStatus {
    ACTIVE,
    CANCELLED,
    FIRED;

    /**
     * Validates that transitioning from {@code from} to {@code to} is allowed.
     *
     * @throws IllegalStateException if the transition is not valid
     */
    public static void validateTransition(CronStatus from, CronStatus to) {
        if (from == to) {
            throw new IllegalStateException("Cannot transition from " + from + " to " + to);
        }
        if (from == CANCELLED) {
            throw new IllegalStateException("CANCELLED is a terminal state — no transitions allowed");
        }
        if (from == FIRED) {
            throw new IllegalStateException("FIRED is a terminal state — no transitions allowed");
        }
        boolean valid = switch (from) {
            case ACTIVE -> to == CANCELLED || to == FIRED;
            default -> false;
        };
        if (!valid) {
            throw new IllegalStateException("Invalid transition: " + from + " -> " + to);
        }
    }
}

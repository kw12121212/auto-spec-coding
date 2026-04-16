package org.specdriven.sdk;

import java.util.Objects;

/**
 * Health probe result for a single platform capability domain.
 *
 * @param name    human-readable subsystem name (e.g. "db", "llm", "compiler", "agent")
 * @param status  observed status at probe time
 * @param message optional detail message; null when status is UP
 */
public record SubsystemHealth(String name, SubsystemStatus status, String message) {

    public SubsystemHealth {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    /** Convenience factory for a healthy subsystem. */
    public static SubsystemHealth up(String name) {
        return new SubsystemHealth(name, SubsystemStatus.UP, null);
    }

    /** Convenience factory for a degraded subsystem. */
    public static SubsystemHealth degraded(String name, String message) {
        return new SubsystemHealth(name, SubsystemStatus.DEGRADED, message);
    }

    /** Convenience factory for a down subsystem. */
    public static SubsystemHealth down(String name, String message) {
        return new SubsystemHealth(name, SubsystemStatus.DOWN, message);
    }
}

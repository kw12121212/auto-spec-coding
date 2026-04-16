package org.specdriven.agent.http;

import org.specdriven.sdk.PlatformHealth;
import org.specdriven.sdk.SubsystemHealth;

import java.util.List;
import java.util.Objects;

/**
 * HTTP response model for the {@code GET /platform/health} endpoint.
 *
 * @param overallStatus name of the aggregated {@code SubsystemStatus}
 * @param subsystems    per-domain health entries
 * @param probedAt      epoch milliseconds from the originating {@link PlatformHealth}
 */
public record PlatformHealthResponse(String overallStatus, List<SubsystemEntry> subsystems, long probedAt) {

    public PlatformHealthResponse {
        Objects.requireNonNull(overallStatus, "overallStatus must not be null");
        Objects.requireNonNull(subsystems, "subsystems must not be null");
        subsystems = List.copyOf(subsystems);
    }

    /**
     * Converts a {@link PlatformHealth} domain object to its HTTP response representation.
     */
    public static PlatformHealthResponse from(PlatformHealth health) {
        List<SubsystemEntry> entries = health.subsystems().stream()
                .map(s -> new SubsystemEntry(s.name(), s.status().name(), s.message()))
                .toList();
        return new PlatformHealthResponse(health.overallStatus().name(), entries, health.probedAt());
    }

    /**
     * Per-subsystem health entry in the HTTP response.
     *
     * @param name    subsystem name
     * @param status  status name (UP, DEGRADED, DOWN)
     * @param message optional detail; null when healthy
     */
    public record SubsystemEntry(String name, String status, String message) {
        public SubsystemEntry {
            Objects.requireNonNull(name, "name must not be null");
            Objects.requireNonNull(status, "status must not be null");
        }
    }
}

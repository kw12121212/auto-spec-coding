package org.specdriven.sdk;

import java.util.List;
import java.util.Objects;

/**
 * Aggregated health-check result for the LealonePlatform.
 * {@code overallStatus} is the worst-case status across all subsystems:
 * DOWN takes precedence over DEGRADED, which takes precedence over UP.
 *
 * @param overallStatus worst-case status derived from subsystems
 * @param subsystems    per-domain health entries
 * @param probedAt      epoch milliseconds at which the probe completed
 */
public record PlatformHealth(SubsystemStatus overallStatus, List<SubsystemHealth> subsystems, long probedAt) {

    public PlatformHealth {
        Objects.requireNonNull(overallStatus, "overallStatus must not be null");
        Objects.requireNonNull(subsystems, "subsystems must not be null");
        subsystems = List.copyOf(subsystems);
    }

    /**
     * Derives the overall (worst-case) status from a list of subsystem health entries.
     */
    public static SubsystemStatus deriveOverall(List<SubsystemHealth> subsystems) {
        SubsystemStatus worst = SubsystemStatus.UP;
        for (SubsystemHealth s : subsystems) {
            if (s.status() == SubsystemStatus.DOWN) {
                return SubsystemStatus.DOWN;
            }
            if (s.status() == SubsystemStatus.DEGRADED) {
                worst = SubsystemStatus.DEGRADED;
            }
        }
        return worst;
    }

    /**
     * Builds a {@code PlatformHealth} from a list of subsystem entries,
     * automatically deriving the overall status.
     */
    public static PlatformHealth of(List<SubsystemHealth> subsystems, long probedAt) {
        return new PlatformHealth(deriveOverall(subsystems), subsystems, probedAt);
    }
}

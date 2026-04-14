package org.specdriven.skill.discovery;

import java.util.List;
import java.util.Objects;

/**
 * Summary of a skill auto-discovery run.
 */
public record DiscoveryResult(
        int registeredCount,
        int failedCount,
        int hotLoadedCount,
        int hotLoadFailedCount,
        List<SkillDiscoveryError> errors) {

    public DiscoveryResult {
        errors = List.copyOf(Objects.requireNonNull(errors, "errors must not be null"));
    }
}

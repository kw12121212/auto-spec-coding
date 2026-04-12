package org.specdriven.agent.agent;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Deterministic retention decision for one context candidate.
 */
public record ContextRetentionDecision(ContextRetentionLevel level, Set<ContextRetentionReason> reasons) {

    public ContextRetentionDecision {
        Objects.requireNonNull(level, "level");
        EnumSet<ContextRetentionReason> copiedReasons = EnumSet.noneOf(ContextRetentionReason.class);
        if (reasons != null) {
            for (ContextRetentionReason reason : reasons) {
                if (reason != null) {
                    copiedReasons.add(reason);
                }
            }
        }
        reasons = Collections.unmodifiableSet(copiedReasons);
    }

    public boolean mandatory() {
        return level == ContextRetentionLevel.MANDATORY;
    }

    public boolean hasReason(ContextRetentionReason reason) {
        return reasons.contains(reason);
    }
}

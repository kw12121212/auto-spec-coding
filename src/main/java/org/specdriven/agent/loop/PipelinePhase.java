package org.specdriven.agent.loop;

import java.util.List;

/**
 * Phases of the spec-driven pipeline, executed in declaration order.
 */
public enum PipelinePhase {
    PROPOSE,
    IMPLEMENT,
    VERIFY,
    REVIEW,
    ARCHIVE;

    /**
     * Returns the classpath resource path for this phase's instruction template.
     */
    public String templateResource() {
        return "/loop-phases/" + name().toLowerCase() + ".txt";
    }

    /**
     * Returns all phases in execution order.
     */
    public static List<PipelinePhase> ordered() {
        return List.of(values());
    }
}

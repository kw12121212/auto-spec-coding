package org.specdriven.agent.loop;

import java.util.Optional;

/**
 * Selects the next change to execute based on roadmap/milestone state.
 */
public interface LoopScheduler {

    /**
     * Selects the next candidate change to execute.
     *
     * @param context the current loop context
     * @return a candidate, or empty if none available
     */
    Optional<LoopCandidate> selectNext(LoopContext context);
}

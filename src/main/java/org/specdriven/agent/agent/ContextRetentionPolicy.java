package org.specdriven.agent.agent;

/**
 * Decides whether a context candidate must survive context optimization.
 */
public interface ContextRetentionPolicy {

    /**
     * Returns a deterministic retention decision for the candidate.
     */
    ContextRetentionDecision evaluate(ContextRetentionCandidate candidate);
}

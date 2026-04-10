package org.specdriven.agent.loop;

/**
 * Pluggable pipeline that executes a single change within the loop.
 * Implementations capture all failures in the returned IterationResult
 * rather than throwing checked exceptions.
 */
public interface LoopPipeline {

    /**
     * Executes the pipeline for the given candidate.
     *
     * @param candidate the change to execute
     * @param config    the loop configuration
     * @return the execution result, never null
     */
    IterationResult execute(LoopCandidate candidate, LoopConfig config);
}

package org.specdriven.agent.loop;

import java.util.Set;

/**
 * Pluggable pipeline that executes a single change within the loop.
 * Implementations capture all failures in the returned IterationResult
 * rather than throwing checked exceptions.
 */
public interface LoopPipeline {

    /**
     * Executes the pipeline for the given candidate, skipping any phases already completed.
     *
     * @param candidate  the change to execute
     * @param config     the loop configuration
     * @param skipPhases phases to skip (already completed before a QUESTIONING interruption)
     * @return the execution result, never null
     */
    IterationResult execute(LoopCandidate candidate, LoopConfig config, Set<PipelinePhase> skipPhases);

    /**
     * Executes the pipeline for the given candidate from the beginning.
     * Delegates to {@link #execute(LoopCandidate, LoopConfig, Set)} with an empty skip set.
     *
     * @param candidate the change to execute
     * @param config    the loop configuration
     * @return the execution result, never null
     */
    default IterationResult execute(LoopCandidate candidate, LoopConfig config) {
        return execute(candidate, config, Set.of());
    }
}

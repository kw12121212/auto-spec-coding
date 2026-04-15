package org.specdriven.agent.loop;

/**
 * Executes one phase of a spec-driven autonomous loop iteration.
 */
@FunctionalInterface
public interface SpecDrivenPhaseRunner {

    /**
     * Runs a single pipeline phase for the selected roadmap candidate.
     *
     * @param phase the phase to run
     * @param candidate the selected roadmap change
     * @param config loop configuration
     * @return structured phase execution result
     */
    PhaseExecutionResult run(PipelinePhase phase, LoopCandidate candidate, LoopConfig config);
}

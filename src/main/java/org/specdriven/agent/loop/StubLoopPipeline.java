package org.specdriven.agent.loop;

import java.util.List;
import java.util.Set;

/**
 * No-op pipeline for backward compatibility with the two-arg
 * DefaultLoopDriver constructor.
 */
class StubLoopPipeline implements LoopPipeline {

    @Override
    public IterationResult execute(LoopCandidate candidate, LoopConfig config, Set<PipelinePhase> skipPhases) {
        return new IterationResult(IterationStatus.SUCCESS, null, 0, List.of());
    }
}

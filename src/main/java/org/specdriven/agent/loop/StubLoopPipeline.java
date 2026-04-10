package org.specdriven.agent.loop;

import java.util.List;

/**
 * No-op pipeline for backward compatibility with the two-arg
 * DefaultLoopDriver constructor.
 */
class StubLoopPipeline implements LoopPipeline {

    @Override
    public IterationResult execute(LoopCandidate candidate, LoopConfig config) {
        return new IterationResult(IterationStatus.SUCCESS, null, 0, List.of());
    }
}

package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class PipelinePhaseTest {

    @Test
    void orderedReturnsAllPhasesInSequence() {
        List<PipelinePhase> phases = PipelinePhase.ordered();
        assertEquals(List.of(
                PipelinePhase.RECOMMEND,
                PipelinePhase.PROPOSE,
                PipelinePhase.IMPLEMENT,
                PipelinePhase.VERIFY,
                PipelinePhase.REVIEW,
                PipelinePhase.ARCHIVE
        ), phases);
    }

    @Test
    void templateResourcePathsFollowConvention() {
        for (PipelinePhase phase : PipelinePhase.values()) {
            String path = phase.templateResource();
            assertTrue(path.startsWith("/loop-phases/"), "Unexpected prefix: " + path);
            assertTrue(path.endsWith(".txt"), "Unexpected suffix: " + path);
            assertEquals("/loop-phases/" + phase.name().toLowerCase() + ".txt", path);
        }
    }

    @Test
    void templateResourceForPropose() {
        assertEquals("/loop-phases/propose.txt", PipelinePhase.PROPOSE.templateResource());
    }

    @Test
    void templateResourceForRecommend() {
        assertEquals("/loop-phases/recommend.txt", PipelinePhase.RECOMMEND.templateResource());
    }

    @Test
    void templateResourceForArchive() {
        assertEquals("/loop-phases/archive.txt", PipelinePhase.ARCHIVE.templateResource());
    }
}

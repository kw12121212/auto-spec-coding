package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class LoopProgressTest {

    @Test
    void nullCompletedChangeNamesNormalizedToEmpty() {
        LoopProgress p = new LoopProgress(LoopState.IDLE, null, 0);
        assertTrue(p.completedChangeNames().isEmpty());
    }

    @Test
    void defensiveCopy() {
        Set<String> names = new java.util.HashSet<>(Set.of("a", "b"));
        LoopProgress p = new LoopProgress(LoopState.RUNNING, names, 3);
        names.add("c");
        assertEquals(2, p.completedChangeNames().size());
    }

    @Test
    void immutableSet() {
        LoopProgress p = new LoopProgress(LoopState.IDLE, Set.of("a"), 0);
        assertThrows(UnsupportedOperationException.class,
                () -> p.completedChangeNames().add("x"));
    }

    @Test
    void negativeTotalIterationsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new LoopProgress(LoopState.IDLE, Set.of(), -1));
    }

    @Test
    void zeroTotalIterationsAllowed() {
        assertDoesNotThrow(() -> new LoopProgress(LoopState.IDLE, Set.of(), 0));
    }

    @Test
    void recordEquality() {
        LoopProgress a = new LoopProgress(LoopState.RUNNING, Set.of("x"), 1);
        LoopProgress b = new LoopProgress(LoopState.RUNNING, Set.of("x"), 1);
        assertEquals(a, b);
    }

    @Test
    void progressWithoutActiveWorkHasNoCheckpoint() {
        LoopProgress p = new LoopProgress(LoopState.IDLE, Set.of(), 0);
        assertTrue(p.activeCheckpoint().isEmpty());
    }

    @Test
    void activeCheckpointExposesSelectedCandidate() {
        LoopPhaseCheckpoint checkpoint = new LoopPhaseCheckpoint(
                "change-a", "m35.md", "goal", "summary",
                List.of(PipelinePhase.RECOMMEND));
        LoopProgress p = new LoopProgress(LoopState.RUNNING, Set.of(), 0, 0, checkpoint);

        assertTrue(p.activeCheckpoint().isPresent());
        LoopPhaseCheckpoint loaded = p.activeCheckpoint().orElseThrow();
        assertEquals("change-a", loaded.changeName());
        assertEquals("m35.md", loaded.milestoneFile());
        assertEquals("goal", loaded.milestoneGoal());
        assertEquals("summary", loaded.plannedChangeSummary());
    }

    @Test
    void checkpointPhaseDataIsDefensivelyCopiedAndOrdered() {
        List<PipelinePhase> phases = new ArrayList<>();
        phases.add(PipelinePhase.VERIFY);
        phases.add(PipelinePhase.RECOMMEND);
        phases.add(PipelinePhase.VERIFY);

        LoopPhaseCheckpoint checkpoint = new LoopPhaseCheckpoint(
                "change-a", "m35.md", null, null, phases);
        phases.add(PipelinePhase.ARCHIVE);

        assertEquals(List.of(PipelinePhase.RECOMMEND, PipelinePhase.VERIFY),
                checkpoint.completedPhases());
        assertThrows(UnsupportedOperationException.class,
                () -> checkpoint.completedPhases().add(PipelinePhase.ARCHIVE));
        assertEquals("", checkpoint.milestoneGoal());
        assertEquals("", checkpoint.plannedChangeSummary());
    }

    @Test
    void checkpointBuildsCandidateForResume() {
        LoopCandidate candidate = new LoopCandidate("change-a", "m35.md", "goal", "summary");
        LoopPhaseCheckpoint checkpoint = new LoopPhaseCheckpoint(candidate,
                List.of(PipelinePhase.RECOMMEND));

        assertEquals(candidate, checkpoint.candidate());
    }
}

package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class IterationResultTest {

    @Test
    void createsSuccessResult() {
        IterationResult result = new IterationResult(
                IterationStatus.SUCCESS, null, 1000, List.of(PipelinePhase.PROPOSE));
        assertEquals(IterationStatus.SUCCESS, result.status());
        assertNull(result.failureReason());
        assertEquals(1000, result.durationMs());
        assertEquals(List.of(PipelinePhase.PROPOSE), result.phasesCompleted());
    }

    @Test
    void createsFailedResult() {
        IterationResult result = new IterationResult(
                IterationStatus.FAILED, "something went wrong", 500,
                List.of(PipelinePhase.PROPOSE, PipelinePhase.IMPLEMENT));
        assertEquals(IterationStatus.FAILED, result.status());
        assertEquals("something went wrong", result.failureReason());
        assertEquals(2, result.phasesCompleted().size());
    }

    @Test
    void normalizesNullPhasesToEmpty() {
        IterationResult result = new IterationResult(IterationStatus.SUCCESS, null, 0, null);
        assertNotNull(result.phasesCompleted());
        assertTrue(result.phasesCompleted().isEmpty());
    }

    @Test
    void defensivelyCopiesPhases() {
        List<PipelinePhase> phases = new java.util.ArrayList<>(List.of(PipelinePhase.PROPOSE));
        IterationResult result = new IterationResult(IterationStatus.SUCCESS, null, 0, phases);
        phases.add(PipelinePhase.IMPLEMENT);
        assertEquals(1, result.phasesCompleted().size());
    }

    @Test
    void phasesListIsImmutable() {
        IterationResult result = new IterationResult(
                IterationStatus.SUCCESS, null, 0, List.of(PipelinePhase.PROPOSE));
        assertThrows(UnsupportedOperationException.class,
                () -> result.phasesCompleted().add(PipelinePhase.IMPLEMENT));
    }

    @Test
    void rejectsNegativeDuration() {
        assertThrows(IllegalArgumentException.class,
                () -> new IterationResult(IterationStatus.SUCCESS, null, -1, List.of()));
    }

    @Test
    void zeroDurationIsValid() {
        assertDoesNotThrow(
                () -> new IterationResult(IterationStatus.SUCCESS, null, 0, List.of()));
    }
}

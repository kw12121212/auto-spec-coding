package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.question.DeliveryMode;
import org.specdriven.agent.question.Question;
import org.specdriven.agent.question.QuestionCategory;
import org.specdriven.agent.question.QuestionStatus;

class IterationResultTest {

    private static Question sampleQuestion() {
        return new Question("q1", "session1", "What approach?", "Critical", "Use A",
                QuestionStatus.WAITING_FOR_ANSWER, QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PAUSE_WAIT_HUMAN);
    }

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

    // --- QUESTIONING status ---

    @Test
    void questioningRequiresNonNullQuestion() {
        assertThrows(IllegalArgumentException.class,
                () -> new IterationResult(IterationStatus.QUESTIONING, null, 100, List.of(), 0, null));
    }

    @Test
    void questioningWithNonNullQuestionIsValid() {
        Question q = sampleQuestion();
        IterationResult result = new IterationResult(
                IterationStatus.QUESTIONING, null, 100, List.of(PipelinePhase.PROPOSE), 0, q);
        assertEquals(IterationStatus.QUESTIONING, result.status());
        assertNotNull(result.question());
        assertEquals("q1", result.question().questionId());
    }

    @Test
    void successWithNonNullQuestionThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new IterationResult(IterationStatus.SUCCESS, null, 100, List.of(), 0, sampleQuestion()));
    }

    @Test
    void failedWithNonNullQuestionThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> new IterationResult(IterationStatus.FAILED, "err", 100, List.of(), 0, sampleQuestion()));
    }

    @Test
    void backwardCompatConstructorHasNullQuestion() {
        IterationResult result = new IterationResult(IterationStatus.SUCCESS, null, 0, List.of());
        assertNull(result.question());
    }

    @Test
    void fiveArgConstructorHasNullQuestion() {
        IterationResult result = new IterationResult(IterationStatus.SUCCESS, null, 0, List.of(), 100);
        assertNull(result.question());
        assertEquals(100, result.tokenUsage());
    }
}

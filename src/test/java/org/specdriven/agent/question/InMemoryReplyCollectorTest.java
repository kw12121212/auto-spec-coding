package org.specdriven.agent.question;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.SimpleEventBus;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryReplyCollectorTest {

    private InMemoryReplyCollector collector;
    private QuestionRuntime runtime;

    @BeforeEach
    void setUp() {
        SimpleEventBus eventBus = new SimpleEventBus();
        runtime = new QuestionRuntime(eventBus);
        collector = new InMemoryReplyCollector(runtime);
    }

    @Test
    void collect_validAnswer_succeeds() {
        Question q = waitingQuestion("q-1", "s-1", DeliveryMode.PAUSE_WAIT_HUMAN);
        runtime.beginWaitingQuestion(q);

        Answer answer = humanAnswer(DeliveryMode.PAUSE_WAIT_HUMAN);
        assertDoesNotThrow(() -> collector.collect("s-1", "q-1", answer));
    }

    @Test
    void collect_mismatchedQuestionId_throws() {
        Question q = waitingQuestion("q-1", "s-1", DeliveryMode.PAUSE_WAIT_HUMAN);
        runtime.beginWaitingQuestion(q);

        Answer answer = humanAnswer(DeliveryMode.PAUSE_WAIT_HUMAN);
        assertThrows(IllegalArgumentException.class,
                () -> collector.collect("s-1", "q-wrong", answer));
    }

    @Test
    void collect_mismatchedDeliveryMode_throws() {
        Question q = waitingQuestion("q-1", "s-1", DeliveryMode.PAUSE_WAIT_HUMAN);
        runtime.beginWaitingQuestion(q);

        Answer answer = humanAnswer(DeliveryMode.PUSH_MOBILE_WAIT_HUMAN);
        assertThrows(IllegalArgumentException.class,
                () -> collector.collect("s-1", "q-1", answer));
    }

    @Test
    void collect_noWaitingQuestion_throws() {
        Answer answer = humanAnswer(DeliveryMode.PAUSE_WAIT_HUMAN);
        assertThrows(IllegalStateException.class,
                () -> collector.collect("s-unknown", "q-1", answer));
    }

    @Test
    void close_doesNotThrow() {
        assertDoesNotThrow(collector::close);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Question waitingQuestion(String questionId, String sessionId, DeliveryMode mode) {
        return new Question(
                questionId, sessionId,
                "Should we continue?",
                "Cannot proceed.",
                "Safe option.",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION,
                mode
        );
    }

    private static Answer humanAnswer(DeliveryMode mode) {
        return new Answer(
                "Yes.", "Confirmed.", "operator:zoe",
                AnswerSource.HUMAN_INLINE, 1.0d,
                QuestionDecision.ANSWER_ACCEPTED,
                mode, "Approval.", System.currentTimeMillis()
        );
    }
}

package org.specdriven.agent.question;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.SimpleEventBus;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class QuestionDeliveryServiceTest {

    private QuestionDeliveryService service;
    private StubChannel channel;
    private InMemoryReplyCollector collector;
    private QuestionRuntime runtime;

    @BeforeEach
    void setUp() {
        SimpleEventBus eventBus = new SimpleEventBus();
        runtime = new QuestionRuntime(eventBus);
        QuestionStore store = new InMemoryQuestionStore();
        runtime.setQuestionStore(store);
        channel = new StubChannel();
        collector = new InMemoryReplyCollector(runtime);
        service = new QuestionDeliveryService(channel, collector, runtime, store);
    }

    @Test
    void deliver_sendsToChannel() {
        Question q = waitingQuestion("q-1", "s-1");
        service.deliver(q);
        assertEquals(1, channel.sent.size());
        assertEquals("q-1", channel.sent.get(0).questionId());
    }

    @Test
    void submitReply_validAnswer_succeeds() {
        Question q = waitingQuestion("q-1", "s-1");
        runtime.beginWaitingQuestion(q);

        Answer answer = humanAnswer("q-1", DeliveryMode.PAUSE_WAIT_HUMAN);
        service.submitReply("s-1", "q-1", answer);
        // No exception = success
    }

    @Test
    void submitReply_unknownSession_throws() {
        Answer answer = humanAnswer("q-1", DeliveryMode.PAUSE_WAIT_HUMAN);
        assertThrows(IllegalStateException.class,
                () -> service.submitReply("unknown", "q-1", answer));
    }

    @Test
    void pendingQuestion_returnsWaitingQuestion() {
        Question q = waitingQuestion("q-1", "s-1");
        runtime.beginWaitingQuestion(q);

        Optional<Question> pending = service.pendingQuestion("s-1");
        assertTrue(pending.isPresent());
        assertEquals("q-1", pending.get().questionId());
    }

    @Test
    void pendingQuestion_emptyWhenNone() {
        assertTrue(service.pendingQuestion("no-session").isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Question waitingQuestion(String questionId, String sessionId) {
        return new Question(
                questionId, sessionId,
                "Should we continue?",
                "Cannot proceed without decision.",
                "Use safest option.",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PAUSE_WAIT_HUMAN
        );
    }

    private static Answer humanAnswer(String questionId, DeliveryMode mode) {
        return new Answer(
                "Yes, proceed.",
                "Confirmed safe.",
                "operator:zoe",
                AnswerSource.HUMAN_INLINE,
                1.0d,
                QuestionDecision.ANSWER_ACCEPTED,
                mode,
                "Human approval.",
                System.currentTimeMillis()
        );
    }

    private static class StubChannel implements QuestionDeliveryChannel {
        final List<Question> sent = new ArrayList<>();

        @Override
        public void send(Question question) {
            sent.add(question);
        }

        @Override
        public void close() {}
    }

    private static class InMemoryQuestionStore implements QuestionStore {
        private final Map<String, Question> data = new LinkedHashMap<>();

        @Override
        public String save(Question question) {
            data.put(question.questionId(), question);
            return question.questionId();
        }

        @Override
        public Question update(String questionId, QuestionStatus status) {
            Question existing = data.get(questionId);
            if (existing == null) throw new NoSuchElementException(questionId);
            Question updated = new Question(
                    existing.questionId(), existing.sessionId(),
                    existing.question(), existing.impact(), existing.recommendation(),
                    status, existing.category(), existing.deliveryMode());
            data.put(questionId, updated);
            return updated;
        }

        @Override
        public List<Question> findBySession(String sessionId) {
            return data.values().stream()
                    .filter(q -> q.sessionId().equals(sessionId))
                    .toList();
        }

        @Override
        public List<Question> findByStatus(QuestionStatus status) {
            return data.values().stream()
                    .filter(q -> q.status() == status)
                    .toList();
        }

        @Override
        public Optional<Question> findPending(String sessionId) {
            return data.values().stream()
                    .filter(q -> q.sessionId().equals(sessionId)
                            && q.status() == QuestionStatus.WAITING_FOR_ANSWER)
                    .findFirst();
        }

        @Override
        public void delete(String questionId) {
            data.remove(questionId);
        }
    }
}

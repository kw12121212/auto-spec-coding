package org.specdriven.sdk;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.question.*;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;

class SdkAgentQuestionTest {

    @Test
    void pendingQuestions_noDeliveryService_returnsEmptyList() {
        SdkAgent agent = new SdkAgent(
                null, java.util.Collections.emptyMap(),
                SdkConfig.defaults(), null, null, null, null);

        List<Question> result = agent.pendingQuestions("s-1");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void submitHumanReply_noDeliveryService_throwsSdkException() {
        SdkAgent agent = new SdkAgent(
                null, java.util.Collections.emptyMap(),
                SdkConfig.defaults(), null, null, null, null);

        Answer answer = new Answer(
                "Yes.", "Confirmed.", "op:zoe",
                AnswerSource.HUMAN_INLINE, 1.0d,
                QuestionDecision.ANSWER_ACCEPTED,
                DeliveryMode.PAUSE_WAIT_HUMAN,
                "Approval.", System.currentTimeMillis());

        SdkException ex = assertThrows(SdkException.class,
                () -> agent.submitHumanReply("s-1", "q-1", answer));
        assertFalse(ex.isRetryable());
    }

    @Test
    void submitHumanReply_unknownSession_throwsNonRetryableSdkException() {
        QuestionDeliveryService service = createTestDeliveryService();
        SdkAgent agent = new SdkAgent(
                null, java.util.Collections.emptyMap(),
                SdkConfig.defaults(), null, null, null, service);

        Answer answer = new Answer(
                "Yes.", "Confirmed.", "op:zoe",
                AnswerSource.HUMAN_INLINE, 1.0d,
                QuestionDecision.ANSWER_ACCEPTED,
                DeliveryMode.PAUSE_WAIT_HUMAN,
                "Approval.", System.currentTimeMillis());

        SdkException ex = assertThrows(SdkException.class,
                () -> agent.submitHumanReply("unknown", "q-1", answer));
        assertFalse(ex.isRetryable());
    }

    @Test
    void pendingQuestions_withWaitingQuestion_returnsList() {
        QuestionDeliveryService service = createTestDeliveryService();
        Question q = new Question(
                "q-1", "s-1", "Continue?",
                "Cannot proceed.", "Use safe option.",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PAUSE_WAIT_HUMAN);
        service.runtime().beginWaitingQuestion(q);

        SdkAgent agent = new SdkAgent(
                null, java.util.Collections.emptyMap(),
                SdkConfig.defaults(), null, null, null, service);

        List<Question> pending = agent.pendingQuestions("s-1");
        assertEquals(1, pending.size());
        assertEquals("q-1", pending.get(0).questionId());
    }

    @Test
    void pendingQuestions_emptyWhenNoWaiting() {
        QuestionDeliveryService service = createTestDeliveryService();
        SdkAgent agent = new SdkAgent(
                null, java.util.Collections.emptyMap(),
                SdkConfig.defaults(), null, null, null, service);

        assertTrue(agent.pendingQuestions("no-session").isEmpty());
    }

    private static QuestionDeliveryService createTestDeliveryService() {
        org.specdriven.agent.event.SimpleEventBus eventBus =
                new org.specdriven.agent.event.SimpleEventBus();
        QuestionRuntime runtime = new QuestionRuntime(eventBus);
        QuestionStore store = new InMemoryQuestionStore();
        runtime.setQuestionStore(store);
        return new QuestionDeliveryService(
                new LoggingDeliveryChannel(),
                new InMemoryReplyCollector(runtime),
                runtime, store);
    }

    private static class InMemoryQuestionStore implements QuestionStore {
        private final java.util.Map<String, Question> data = new java.util.LinkedHashMap<>();

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
        public java.util.Optional<Question> findPending(String sessionId) {
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

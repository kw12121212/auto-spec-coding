package org.specdriven.agent.question;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class LealoneQuestionStoreTest {

    private LealoneQuestionStore store;

    @BeforeEach
    void setUp() {
        String dbName = "test_questions_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
        store = new LealoneQuestionStore(
                new NoOpEventBus(), jdbcUrl);
    }

    // -------------------------------------------------------------------------
    // save / update
    // -------------------------------------------------------------------------

    @Test
    void save_persistsQuestion() {
        Question q = waitingQuestion("q-1", "s-1");
        String id = store.save(q);
        assertEquals("q-1", id);

        Optional<Question> loaded = store.load(id);
        assertTrue(loaded.isPresent());
        assertEquals("Should we continue?", loaded.get().question());
        assertEquals(QuestionStatus.WAITING_FOR_ANSWER, loaded.get().status());
    }

    @Test
    void update_changesStatus() {
        store.save(waitingQuestion("q-2", "s-2"));
        Question updated = store.update("q-2", QuestionStatus.ANSWERED);
        assertEquals(QuestionStatus.ANSWERED, updated.status());
    }

    @Test
    void update_nonExistent_throws() {
        assertThrows(NoSuchElementException.class,
                () -> store.update("missing", QuestionStatus.ANSWERED));
    }

    // -------------------------------------------------------------------------
    // findBySession
    // -------------------------------------------------------------------------

    @Test
    void findBySession_returnsMatching() {
        store.save(waitingQuestion("q-3", "s-3"));
        store.save(waitingQuestion("q-4", "s-3"));
        store.save(waitingQuestion("q-5", "s-other"));

        List<Question> results = store.findBySession("s-3");
        assertEquals(2, results.size());
    }

    @Test
    void findBySession_emptyWhenNoMatch() {
        assertTrue(store.findBySession("no-session").isEmpty());
    }

    // -------------------------------------------------------------------------
    // findByStatus
    // -------------------------------------------------------------------------

    @Test
    void findByStatus_returnsMatching() {
        store.save(waitingQuestion("q-6", "s-6"));
        store.save(waitingQuestion("q-7", "s-7"));
        store.update("q-7", QuestionStatus.ANSWERED);

        List<Question> waiting = store.findByStatus(QuestionStatus.WAITING_FOR_ANSWER);
        assertEquals(1, waiting.size());
        assertEquals("q-6", waiting.get(0).questionId());

        List<Question> answered = store.findByStatus(QuestionStatus.ANSWERED);
        assertEquals(1, answered.size());
        assertEquals("q-7", answered.get(0).questionId());
    }

    // -------------------------------------------------------------------------
    // findPending
    // -------------------------------------------------------------------------

    @Test
    void findPending_returnsWaitingQuestion() {
        store.save(waitingQuestion("q-8", "s-8"));

        Optional<Question> pending = store.findPending("s-8");
        assertTrue(pending.isPresent());
        assertEquals("q-8", pending.get().questionId());
    }

    @Test
    void findPending_emptyWhenAnswered() {
        store.save(waitingQuestion("q-9", "s-9"));
        store.update("q-9", QuestionStatus.ANSWERED);

        assertTrue(store.findPending("s-9").isEmpty());
    }

    @Test
    void findPending_emptyWhenNoQuestions() {
        assertTrue(store.findPending("no-session").isEmpty());
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_removesQuestion() {
        store.save(waitingQuestion("q-10", "s-10"));
        store.delete("q-10");
        assertTrue(store.load("q-10").isEmpty());
    }

    // -------------------------------------------------------------------------
    // Full lifecycle
    // -------------------------------------------------------------------------

    @Test
    void fullLifecycle() {
        // Save
        store.save(waitingQuestion("q-lifecycle", "s-lc"));
        assertTrue(store.findPending("s-lc").isPresent());

        // Update to ANSWERED
        store.update("q-lifecycle", QuestionStatus.ANSWERED);
        assertTrue(store.findPending("s-lc").isEmpty());
        assertEquals(QuestionStatus.ANSWERED,
                store.findByStatus(QuestionStatus.ANSWERED).get(0).status());

        // Delete
        store.delete("q-lifecycle");
        assertTrue(store.load("q-lifecycle").isEmpty());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Question waitingQuestion(String questionId, String sessionId) {
        return new Question(
                questionId, sessionId,
                "Should we continue?",
                "The workflow cannot proceed without a decision.",
                "Use the safest documented option.",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PAUSE_WAIT_HUMAN
        );
    }

    private static class NoOpEventBus implements org.specdriven.agent.event.EventBus {
        @Override
        public void publish(org.specdriven.agent.event.Event event) {}
        @Override
        public void subscribe(org.specdriven.agent.event.EventType type, java.util.function.Consumer<org.specdriven.agent.event.Event> listener) {}
        @Override
        public void unsubscribe(org.specdriven.agent.event.EventType type, java.util.function.Consumer<org.specdriven.agent.event.Event> listener) {}
    }
}

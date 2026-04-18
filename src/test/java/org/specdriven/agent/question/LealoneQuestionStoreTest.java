package org.specdriven.agent.question;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import org.specdriven.agent.testsupport.LealoneTestDb;

class LealoneQuestionStoreTest {

    private String jdbcUrl;
    private LealoneQuestionStore store;

    @BeforeEach
    void setUp() {
        jdbcUrl = LealoneTestDb.freshJdbcUrl();
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
        assertEquals(q, loaded.get());
    }

    @Test
    void save_updatesExistingQuestion() {
        store.save(waitingQuestion("q-upsert", "s-upsert"));
        Question answered = new Question(
                "q-upsert", "s-upsert",
                "Did the operator approve?",
                "The workflow remains paused without approval.",
                "Continue only after approval.",
                QuestionStatus.ANSWERED,
                QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PAUSE_WAIT_HUMAN
        );

        store.save(answered);

        Optional<Question> loaded = store.load("q-upsert");
        assertTrue(loaded.isPresent());
        assertEquals(answered, loaded.get());
    }

    @Test
    void storeSavedQuestion_isVisibleThroughExistingQuestionsTableColumns() throws Exception {
        Question question = waitingQuestion("q-table-visible", "s-table-visible");

        store.save(question);

        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT question_id, session_id, question_text, impact,
                            recommendation, status, category, delivery_mode,
                            created_at, updated_at
                     FROM questions
                     WHERE question_id = ?
                     """)) {
            ps.setString(1, "q-table-visible");

            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(question.questionId(), rs.getString("question_id"));
                assertEquals(question.sessionId(), rs.getString("session_id"));
                assertEquals(question.question(), rs.getString("question_text"));
                assertEquals(question.impact(), rs.getString("impact"));
                assertEquals(question.recommendation(), rs.getString("recommendation"));
                assertEquals(question.status().name(), rs.getString("status"));
                assertEquals(question.category().name(), rs.getString("category"));
                assertEquals(question.deliveryMode().name(), rs.getString("delivery_mode"));
                assertTrue(rs.getLong("created_at") > 0);
                assertTrue(rs.getLong("updated_at") > 0);
                assertFalse(rs.next());
            }
        }
    }

    @Test
    void readsRowsCompatibleWithExistingQuestionsTable() throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             PreparedStatement ps = conn.prepareStatement("""
                     INSERT INTO questions
                         (question_id, session_id, question_text, impact, recommendation,
                          status, category, delivery_mode, created_at, updated_at)
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            ps.setString(1, "q-existing-row");
            ps.setString(2, "s-existing-row");
            ps.setString(3, "Approve the deployment?");
            ps.setString(4, "The deployment is blocked.");
            ps.setString(5, "Approve if checks are green.");
            ps.setString(6, QuestionStatus.WAITING_FOR_ANSWER.name());
            ps.setString(7, QuestionCategory.PERMISSION_CONFIRMATION.name());
            ps.setString(8, DeliveryMode.PAUSE_WAIT_HUMAN.name());
            ps.setLong(9, 10L);
            ps.setLong(10, 11L);
            ps.executeUpdate();
        }

        Optional<Question> loaded = store.load("q-existing-row");
        assertTrue(loaded.isPresent());
        assertEquals(new Question(
                "q-existing-row",
                "s-existing-row",
                "Approve the deployment?",
                "The deployment is blocked.",
                "Approve if checks are green.",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PAUSE_WAIT_HUMAN
        ), loaded.get());
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

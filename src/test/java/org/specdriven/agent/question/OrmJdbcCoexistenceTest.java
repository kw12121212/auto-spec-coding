package org.specdriven.agent.question;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.permission.LealonePolicyStore;
import org.specdriven.agent.permission.Permission;
import org.specdriven.agent.permission.PermissionContext;
import org.specdriven.agent.permission.PermissionDecision;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class OrmJdbcCoexistenceTest {

    @Test
    void sharedDatabaseInitialization_preservesStoreOwnedTables() throws Exception {
        String jdbcUrl = jdbcUrl("coexist_init");

        new LealonePolicyStore(jdbcUrl);
        new LealoneDeliveryLogStore(jdbcUrl);
        new LealoneQuestionStore(new NoOpEventBus(), jdbcUrl);

        assertTableReadable(jdbcUrl, "permission_policies");
        assertTableReadable(jdbcUrl, "permission_audit_log");
        assertTableReadable(jdbcUrl, "delivery_log");
        assertTableReadable(jdbcUrl, "questions");

        new LealoneQuestionStore(new NoOpEventBus(), jdbcUrl);
        new LealoneDeliveryLogStore(jdbcUrl);
        new LealonePolicyStore(jdbcUrl);

        assertTableReadable(jdbcUrl, "permission_policies");
        assertTableReadable(jdbcUrl, "permission_audit_log");
        assertTableReadable(jdbcUrl, "delivery_log");
        assertTableReadable(jdbcUrl, "questions");
    }

    @Test
    void interleavedOrmAndRawJdbcOperations_remainReadableThroughOwningStores() {
        String jdbcUrl = jdbcUrl("coexist_api");
        LealoneDeliveryLogStore deliveryStore = new LealoneDeliveryLogStore(jdbcUrl);
        LealoneQuestionStore questionStore = new LealoneQuestionStore(new NoOpEventBus(), jdbcUrl);
        LealonePolicyStore policyStore = new LealonePolicyStore(jdbcUrl);

        Question question = waitingQuestion("q-coexist", "s-coexist");
        Permission permission = new Permission("execute", "/bin/bash", Map.of("scope", "coexistence"));
        PermissionContext context = new PermissionContext("bash-tool", "run", "agent-coexist");
        DeliveryAttempt attempt = new DeliveryAttempt(
                "q-coexist",
                "telegram",
                1,
                DeliveryStatus.SENT,
                202,
                null,
                1234L
        );

        questionStore.save(question);
        policyStore.grant(permission, context);
        deliveryStore.save(attempt);

        assertEquals(question, questionStore.findPending("s-coexist").orElseThrow());
        assertEquals(List.of(attempt), deliveryStore.findByQuestion("q-coexist"));
        assertEquals(PermissionDecision.ALLOW, policyStore.find(permission, context).orElseThrow());
        assertEquals(1, policyStore.listPolicies().size());
    }

    @Test
    void ormTableInteroperability_survivesRawJdbcStoreUsage() throws Exception {
        String jdbcUrl = jdbcUrl("coexist_table");
        LealonePolicyStore policyStore = new LealonePolicyStore(jdbcUrl);
        LealoneQuestionStore questionStore = new LealoneQuestionStore(new NoOpEventBus(), jdbcUrl);
        LealoneDeliveryLogStore deliveryStore = new LealoneDeliveryLogStore(jdbcUrl);

        Permission permission = new Permission("read", "/workspace", Map.of());
        PermissionContext context = new PermissionContext("read-tool", "read", "agent-coexist");
        policyStore.grant(permission, context);

        Question question = waitingQuestion("q-table-coexist", "s-table-coexist");
        DeliveryAttempt attempt = new DeliveryAttempt(
                "q-table-coexist",
                "discord",
                2,
                DeliveryStatus.FAILED,
                500,
                "failed",
                5678L
        );

        questionStore.save(question);
        deliveryStore.save(attempt);

        assertQuestionTableRow(jdbcUrl, question);
        assertDeliveryLogTableRow(jdbcUrl, attempt);
        assertEquals(PermissionDecision.ALLOW, policyStore.find(permission, context).orElseThrow());
    }

    private static String jdbcUrl(String prefix) {
        String dbName = prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
    }

    private static Question waitingQuestion(String questionId, String sessionId) {
        return new Question(
                questionId,
                sessionId,
                "Should we continue?",
                "The workflow cannot proceed without a decision.",
                "Use the safest documented option.",
                QuestionStatus.WAITING_FOR_ANSWER,
                QuestionCategory.PERMISSION_CONFIRMATION,
                DeliveryMode.PAUSE_WAIT_HUMAN
        );
    }

    private static void assertTableReadable(String jdbcUrl, String tableName) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             Statement stmt = conn.createStatement();
             ResultSet ignored = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertTrue(ignored.next());
        }
    }

    private static void assertQuestionTableRow(String jdbcUrl, Question question) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT question_id, session_id, question_text, impact,
                            recommendation, status, category, delivery_mode
                     FROM questions
                     WHERE question_id = ?
                     """)) {
            ps.setString(1, question.questionId());
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
                assertFalse(rs.next());
            }
        }
    }

    private static void assertDeliveryLogTableRow(String jdbcUrl, DeliveryAttempt attempt) throws Exception {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, "root", "");
             PreparedStatement ps = conn.prepareStatement("""
                     SELECT question_id, channel_type, attempt_number, status,
                            status_code, error_message, attempted_at
                     FROM delivery_log
                     WHERE question_id = ?
                     """)) {
            ps.setString(1, attempt.questionId());
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                assertEquals(attempt.questionId(), rs.getString("question_id"));
                assertEquals(attempt.channelType(), rs.getString("channel_type"));
                assertEquals(attempt.attemptNumber(), rs.getInt("attempt_number"));
                assertEquals(attempt.status().name(), rs.getString("status"));
                assertEquals(attempt.statusCode(), rs.getInt("status_code"));
                assertEquals(attempt.errorMessage(), rs.getString("error_message"));
                assertEquals(attempt.attemptedAt(), rs.getLong("attempted_at"));
                assertFalse(rs.next());
            }
        }
    }

    private static class NoOpEventBus implements EventBus {
        @Override
        public void publish(Event event) {
        }

        @Override
        public void subscribe(EventType type, Consumer<Event> listener) {
        }

        @Override
        public void unsubscribe(EventType type, Consumer<Event> listener) {
        }
    }
}

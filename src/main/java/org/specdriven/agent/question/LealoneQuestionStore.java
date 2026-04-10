package org.specdriven.agent.question;

import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.sql.*;
import java.util.*;

/**
 * {@link QuestionStore} backed by a Lealone SQL table.
 * Table is auto-created on first initialization.
 * A background VirtualThread scans for timed-out questions every 30 seconds.
 */
public class LealoneQuestionStore implements QuestionStore {

    private static final System.Logger LOG =
            System.getLogger(LealoneQuestionStore.class.getName());

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS questions (
                question_id    VARCHAR(36)  PRIMARY KEY,
                session_id     VARCHAR(36)  NOT NULL,
                question_text  CLOB         NOT NULL,
                impact         CLOB         NOT NULL,
                recommendation CLOB         NOT NULL,
                status         VARCHAR(30)  NOT NULL,
                category       VARCHAR(40)  NOT NULL,
                delivery_mode  VARCHAR(40)  NOT NULL,
                created_at     BIGINT       NOT NULL,
                updated_at     BIGINT       NOT NULL
            )
            """;

    private static final long SCAN_INTERVAL_MS = 30_000L;

    private final String jdbcUrl;
    private final EventBus eventBus;
    private volatile boolean closed = false;

    public LealoneQuestionStore(EventBus eventBus, String jdbcUrl) {
        this.eventBus = eventBus;
        this.jdbcUrl = jdbcUrl;
        initTables();
        startTimeoutScanner();
    }

    // -------------------------------------------------------------------------
    // QuestionStore implementation
    // -------------------------------------------------------------------------

    @Override
    public String save(Question question) {
        long now = System.currentTimeMillis();
        String upsert = "MERGE INTO questions (question_id, session_id, question_text, impact, recommendation, status, category, delivery_mode, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setString(1, question.questionId());
            ps.setString(2, question.sessionId());
            ps.setString(3, question.question());
            ps.setString(4, question.impact());
            ps.setString(5, question.recommendation());
            ps.setString(6, question.status().name());
            ps.setString(7, question.category().name());
            ps.setString(8, question.deliveryMode().name());
            ps.setLong(9, now);
            ps.setLong(10, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save question " + question.questionId(), e);
        }

        return question.questionId();
    }

    @Override
    public Question update(String questionId, QuestionStatus status) {
        long now = System.currentTimeMillis();
        String sql = "UPDATE questions SET status = ?, updated_at = ? WHERE question_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status.name());
            ps.setLong(2, now);
            ps.setString(3, questionId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new NoSuchElementException("Question not found: " + questionId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update question " + questionId, e);
        }

        return load(questionId).orElseThrow();
    }

    @Override
    public List<Question> findBySession(String sessionId) {
        String sql = "SELECT question_id, session_id, question_text, impact, recommendation, status, category, delivery_mode, created_at, updated_at FROM questions WHERE session_id = ? ORDER BY created_at ASC";
        return queryQuestions(sql, ps -> ps.setString(1, sessionId));
    }

    @Override
    public List<Question> findByStatus(QuestionStatus status) {
        String sql = "SELECT question_id, session_id, question_text, impact, recommendation, status, category, delivery_mode, created_at, updated_at FROM questions WHERE status = ? ORDER BY created_at ASC";
        return queryQuestions(sql, ps -> ps.setString(1, status.name()));
    }

    @Override
    public Optional<Question> findPending(String sessionId) {
        String sql = "SELECT question_id, session_id, question_text, impact, recommendation, status, category, delivery_mode, created_at, updated_at FROM questions WHERE session_id = ? AND status = ? LIMIT 1";
        List<Question> results = queryQuestions(sql, ps -> {
            ps.setString(1, sessionId);
            ps.setString(2, QuestionStatus.WAITING_FOR_ANSWER.name());
        });
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public void delete(String questionId) {
        String sql = "DELETE FROM questions WHERE question_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, questionId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete question " + questionId, e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    Optional<Question> load(String questionId) {
        String sql = "SELECT question_id, session_id, question_text, impact, recommendation, status, category, delivery_mode, created_at, updated_at FROM questions WHERE question_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(resultSetToQuestion(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load question " + questionId, e);
        }
    }

    private List<Question> queryQuestions(String sql, PreparedStatementSetter setter) {
        List<Question> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setter.setValues(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(resultSetToQuestion(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query questions", e);
        }
        return Collections.unmodifiableList(result);
    }

    private Question resultSetToQuestion(ResultSet rs) throws SQLException {
        return new Question(
                rs.getString("question_id"),
                rs.getString("session_id"),
                rs.getString("question_text"),
                rs.getString("impact"),
                rs.getString("recommendation"),
                QuestionStatus.valueOf(rs.getString("status")),
                QuestionCategory.valueOf(rs.getString("category")),
                DeliveryMode.valueOf(rs.getString("delivery_mode"))
        );
    }

    private void publishEvent(EventType type, String questionId, String sessionId) {
        try {
            eventBus.publish(new Event(type, System.currentTimeMillis(), sessionId,
                    Map.of("questionId", questionId)));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to publish event " + type, e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "root", "");
    }

    private void initTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize questions table", e);
        }
    }

    private void startTimeoutScanner() {
        Thread.ofVirtual().start(() -> {
            while (!closed && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(SCAN_INTERVAL_MS);
                    expireTimedOutQuestions();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.WARNING, "Timeout scan failed", e);
                }
            }
        });
    }

    private void expireTimedOutQuestions() {
        // No explicit timeout column — questions stay in WAITING_FOR_ANSWER
        // until externally expired via update(). The scanner is a hook for
        // future time-based expiry; currently a no-op to match the spec
        // where expiry is driven by QuestionRuntime.
    }

    @FunctionalInterface
    private interface PreparedStatementSetter {
        void setValues(PreparedStatement ps) throws SQLException;
    }
}

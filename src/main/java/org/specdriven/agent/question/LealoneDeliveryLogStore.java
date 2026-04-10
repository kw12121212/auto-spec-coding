package org.specdriven.agent.question;

import java.sql.*;
import java.util.*;

/**
 * {@link DeliveryLogStore} backed by a Lealone SQL table.
 * Table is auto-created on first initialization.
 */
public class LealoneDeliveryLogStore implements DeliveryLogStore {

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS delivery_log (
                id             BIGINT       PRIMARY KEY AUTO_INCREMENT,
                question_id    VARCHAR(36)  NOT NULL,
                channel_type   VARCHAR(30)  NOT NULL,
                attempt_number INT          NOT NULL,
                status         VARCHAR(20)  NOT NULL,
                status_code    INT,
                error_message  CLOB,
                attempted_at   BIGINT       NOT NULL
            )
            """;

    private final String jdbcUrl;

    public LealoneDeliveryLogStore(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        initTables();
    }

    @Override
    public void save(DeliveryAttempt attempt) {
        String sql = "INSERT INTO delivery_log (question_id, channel_type, attempt_number, status, status_code, error_message, attempted_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attempt.questionId());
            ps.setString(2, attempt.channelType());
            ps.setInt(3, attempt.attemptNumber());
            ps.setString(4, attempt.status().name());
            if (attempt.statusCode() != null) {
                ps.setInt(5, attempt.statusCode());
            } else {
                ps.setNull(5, Types.INTEGER);
            }
            ps.setString(6, attempt.errorMessage());
            ps.setLong(7, attempt.attemptedAt());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save delivery attempt", e);
        }
    }

    @Override
    public List<DeliveryAttempt> findByQuestion(String questionId) {
        String sql = "SELECT question_id, channel_type, attempt_number, status, status_code, error_message, attempted_at FROM delivery_log WHERE question_id = ? ORDER BY attempt_number ASC";
        return queryAttempts(sql, ps -> ps.setString(1, questionId));
    }

    @Override
    public Optional<DeliveryAttempt> findLatestByQuestion(String questionId) {
        String sql = "SELECT question_id, channel_type, attempt_number, status, status_code, error_message, attempted_at FROM delivery_log WHERE question_id = ? ORDER BY attempt_number DESC LIMIT 1";
        List<DeliveryAttempt> results = queryAttempts(sql, ps -> ps.setString(1, questionId));
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    // --- Internal helpers ---

    private List<DeliveryAttempt> queryAttempts(String sql, PreparedStatementSetter setter) {
        List<DeliveryAttempt> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            setter.setValues(ps);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(resultSetToAttempt(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query delivery_log", e);
        }
        return Collections.unmodifiableList(result);
    }

    private DeliveryAttempt resultSetToAttempt(ResultSet rs) throws SQLException {
        Integer statusCode = rs.getObject("status_code", Integer.class);
        String errorMessage = rs.getString("error_message");
        return new DeliveryAttempt(
                rs.getString("question_id"),
                rs.getString("channel_type"),
                rs.getInt("attempt_number"),
                DeliveryStatus.valueOf(rs.getString("status")),
                statusCode,
                errorMessage != null && errorMessage.isEmpty() ? null : errorMessage,
                rs.getLong("attempted_at")
        );
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "root", "");
    }

    private void initTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize delivery_log table", e);
        }
    }

    @FunctionalInterface
    private interface PreparedStatementSetter {
        void setValues(PreparedStatement ps) throws SQLException;
    }
}

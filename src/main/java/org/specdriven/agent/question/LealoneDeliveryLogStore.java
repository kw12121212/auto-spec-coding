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
        try {
            DeliveryLogModel model = new DeliveryLogModel(jdbcUrl)
                    .questionId.set(attempt.questionId())
                    .channelType.set(attempt.channelType())
                    .attemptNumber.set(attempt.attemptNumber())
                    .status.set(attempt.status().name())
                    .attemptedAt.set(attempt.attemptedAt());
            if (attempt.statusCode() != null) {
                model.statusCode.set(attempt.statusCode());
            }
            if (attempt.errorMessage() != null) {
                model.errorMessage.set(attempt.errorMessage());
            }
            model.insert();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to save delivery attempt", e);
        }
    }

    @Override
    public List<DeliveryAttempt> findByQuestion(String questionId) {
        try {
            List<DeliveryLogModel> models = DeliveryLogModel.dao(jdbcUrl)
                    .where().questionId.eq(questionId)
                    .orderBy().attemptNumber.asc()
                    .findList();
            return toAttempts(models);
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to query delivery_log", e);
        }
    }

    @Override
    public Optional<DeliveryAttempt> findLatestByQuestion(String questionId) {
        try {
            List<DeliveryAttempt> results = toAttempts(DeliveryLogModel.dao(jdbcUrl)
                    .where().questionId.eq(questionId)
                    .orderBy().attemptNumber.desc()
                    .limit(1)
                    .findList());
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to query delivery_log", e);
        }
    }

    // --- Internal helpers ---

    private List<DeliveryAttempt> toAttempts(List<DeliveryLogModel> models) {
        List<DeliveryAttempt> result = new ArrayList<>(models.size());
        for (DeliveryLogModel model : models) {
            result.add(toAttempt(model));
        }
        return Collections.unmodifiableList(result);
    }

    private DeliveryAttempt toAttempt(DeliveryLogModel model) {
        String errorMessage = model.errorMessage.get();
        return new DeliveryAttempt(
                model.questionId.get(),
                model.channelType.get(),
                model.attemptNumber.get(),
                DeliveryStatus.valueOf(model.status.get()),
                model.statusCode.get(),
                errorMessage != null && errorMessage.isEmpty() ? null : errorMessage,
                model.attemptedAt.get()
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
}

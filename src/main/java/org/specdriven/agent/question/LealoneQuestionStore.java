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
        try {
            QuestionModel existing = QuestionModel.dao(jdbcUrl)
                    .where().questionId.eq(question.questionId())
                    .findOne();
            if (existing == null) {
                model(question, now, now).insert();
            } else {
                updateModel(question, now, now)
                        .where().questionId.eq(question.questionId())
                        .update();
            }
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to save question " + question.questionId(), e);
        }

        return question.questionId();
    }

    @Override
    public Question update(String questionId, QuestionStatus status) {
        long now = System.currentTimeMillis();
        try {
            int rows = QuestionModel.dao(jdbcUrl)
                    .status.set(status.name())
                    .updatedAt.set(now)
                    .where().questionId.eq(questionId)
                    .update();
            if (rows == 0) {
                throw new NoSuchElementException("Question not found: " + questionId);
            }
        } catch (NoSuchElementException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to update question " + questionId, e);
        }

        return load(questionId).orElseThrow();
    }

    @Override
    public List<Question> findBySession(String sessionId) {
        try {
            return toQuestions(QuestionModel.dao(jdbcUrl)
                    .where().sessionId.eq(sessionId)
                    .orderBy().createdAt.asc()
                    .findList());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to query questions", e);
        }
    }

    @Override
    public List<Question> findByStatus(QuestionStatus status) {
        try {
            return toQuestions(QuestionModel.dao(jdbcUrl)
                    .where().status.eq(status.name())
                    .orderBy().createdAt.asc()
                    .findList());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to query questions", e);
        }
    }

    @Override
    public Optional<Question> findPending(String sessionId) {
        List<Question> results;
        try {
            results = toQuestions(QuestionModel.dao(jdbcUrl)
                    .where().sessionId.eq(sessionId)
                    .and().status.eq(QuestionStatus.WAITING_FOR_ANSWER.name())
                    .orderBy().createdAt.asc()
                    .limit(1)
                    .findList());
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to query questions", e);
        }
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public void delete(String questionId) {
        try {
            QuestionModel.dao(jdbcUrl)
                    .where().questionId.eq(questionId)
                    .delete();
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to delete question " + questionId, e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    Optional<Question> load(String questionId) {
        try {
            QuestionModel model = QuestionModel.dao(jdbcUrl)
                    .where().questionId.eq(questionId)
                    .findOne();
            return model == null ? Optional.empty() : Optional.of(toQuestion(model));
        } catch (RuntimeException e) {
            throw new IllegalStateException("Failed to load question " + questionId, e);
        }
    }

    private QuestionModel model(Question question, long createdAt, long updatedAt) {
        return populate(new QuestionModel(jdbcUrl), question, createdAt, updatedAt);
    }

    private QuestionModel updateModel(Question question, long createdAt, long updatedAt) {
        return populate(QuestionModel.dao(jdbcUrl), question, createdAt, updatedAt);
    }

    private QuestionModel populate(QuestionModel model, Question question, long createdAt, long updatedAt) {
        return model
                .questionId.set(question.questionId())
                .sessionId.set(question.sessionId())
                .questionText.set(question.question())
                .impact.set(question.impact())
                .recommendation.set(question.recommendation())
                .status.set(question.status().name())
                .category.set(question.category().name())
                .deliveryMode.set(question.deliveryMode().name())
                .createdAt.set(createdAt)
                .updatedAt.set(updatedAt);
    }

    private List<Question> toQuestions(List<QuestionModel> models) {
        List<Question> result = new ArrayList<>(models.size());
        for (QuestionModel model : models) {
            result.add(toQuestion(model));
        }
        return Collections.unmodifiableList(result);
    }

    private Question toQuestion(QuestionModel model) {
        return new Question(
                model.questionId.get(),
                model.sessionId.get(),
                model.questionText.get(),
                model.impact.get(),
                model.recommendation.get(),
                QuestionStatus.valueOf(model.status.get()),
                QuestionCategory.valueOf(model.category.get()),
                DeliveryMode.valueOf(model.deliveryMode.get())
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

}

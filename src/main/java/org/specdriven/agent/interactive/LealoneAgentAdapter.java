package org.specdriven.agent.interactive;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.UUID;

/**
 * Lealone-backed {@link InteractiveSession} adapter for SQL and natural-language input.
 */
public final class LealoneAgentAdapter implements InteractiveSession {

    /**
     * Opens the Lealone execution resource used by one adapter instance.
     */
    @FunctionalInterface
    public interface LealoneExecutionSessionFactory {

        LealoneExecutionSession open() throws Exception;
    }

    /**
     * Executes accepted interactive input and returns textual output in emission order.
     */
    public interface LealoneExecutionSession extends AutoCloseable {

        List<String> execute(String input) throws Exception;

        @Override
        void close() throws Exception;
    }

    private final String sessionId;
    private final LealoneExecutionSessionFactory executionSessionFactory;
    private final List<String> outputBuffer;

    private InteractiveSessionState state;
    private LealoneExecutionSession executionSession;

    public LealoneAgentAdapter(String jdbcUrl) {
        this(jdbcUrl, "root", "");
    }

    public LealoneAgentAdapter(String jdbcUrl, String user, String password) {
        this(createJdbcExecutionSessionFactory(jdbcUrl, user, password));
    }

    public LealoneAgentAdapter(LealoneExecutionSessionFactory executionSessionFactory) {
        this.sessionId = UUID.randomUUID().toString();
        this.executionSessionFactory = Objects.requireNonNull(executionSessionFactory,
                "executionSessionFactory");
        this.outputBuffer = new ArrayList<>();
        this.state = InteractiveSessionState.NEW;
    }

    @Override
    public String sessionId() {
        return sessionId;
    }

    @Override
    public synchronized InteractiveSessionState state() {
        return state;
    }

    @Override
    public synchronized void start() {
        if (state != InteractiveSessionState.NEW) {
            throw new IllegalStateException(
                    "start() requires state NEW, current: " + state);
        }
        try {
            executionSession = Objects.requireNonNull(executionSessionFactory.open(),
                    "executionSession");
            state = InteractiveSessionState.ACTIVE;
        } catch (Exception e) {
            state = InteractiveSessionState.ERROR;
            throw new IllegalStateException("Failed to start Lealone interactive session", e);
        }
    }

    @Override
    public synchronized void submit(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Input must not be null or blank");
        }
        if (state != InteractiveSessionState.ACTIVE) {
            throw new IllegalStateException(
                    "submit() requires state ACTIVE, current: " + state);
        }
        try {
            outputBuffer.addAll(executionSession.execute(input));
        } catch (Exception e) {
            state = InteractiveSessionState.ERROR;
            throw new IllegalStateException("Lealone execution failed", e);
        }
    }

    @Override
    public synchronized List<String> drainOutput() {
        if (outputBuffer.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> snapshot = Collections.unmodifiableList(new ArrayList<>(outputBuffer));
        outputBuffer.clear();
        return snapshot;
    }

    @Override
    public synchronized void close() {
        if (state == InteractiveSessionState.CLOSED) {
            return;
        }
        try {
            if (executionSession != null) {
                executionSession.close();
            }
        } catch (Exception e) {
            throw new IllegalStateException("Failed to close Lealone interactive session", e);
        } finally {
            executionSession = null;
            state = InteractiveSessionState.CLOSED;
        }
    }

    private static LealoneExecutionSessionFactory createJdbcExecutionSessionFactory(String jdbcUrl,
            String user, String password) {
        if (jdbcUrl == null || jdbcUrl.isBlank()) {
            throw new IllegalArgumentException("jdbcUrl must not be null or blank");
        }
        return () -> new JdbcLealoneExecutionSession(
                DriverManager.getConnection(jdbcUrl, user, password));
    }

    private static final class JdbcLealoneExecutionSession implements LealoneExecutionSession {

        private final Connection connection;
        private final Statement statement;

        private JdbcLealoneExecutionSession(Connection connection) throws SQLException {
            this.connection = connection;
            this.statement = connection.createStatement();
        }

        @Override
        public List<String> execute(String input) throws SQLException {
            if (statement.execute(input)) {
                try (ResultSet resultSet = statement.getResultSet()) {
                    return formatResultSet(resultSet);
                }
            }
            int updateCount = statement.getUpdateCount();
            if (updateCount >= 0) {
                return List.of("Update count: " + updateCount);
            }
            return Collections.emptyList();
        }

        @Override
        public void close() throws SQLException {
            SQLException failure = null;
            try {
                statement.close();
            } catch (SQLException e) {
                failure = e;
            }
            try {
                connection.close();
            } catch (SQLException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
            if (failure != null) {
                throw failure;
            }
        }

        private static List<String> formatResultSet(ResultSet resultSet) throws SQLException {
            ResultSetMetaData metadata = resultSet.getMetaData();
            int columnCount = metadata.getColumnCount();
            List<String> output = new ArrayList<>();
            output.add(formatHeader(metadata, columnCount));
            while (resultSet.next()) {
                StringJoiner row = new StringJoiner("\t");
                for (int column = 1; column <= columnCount; column++) {
                    String value = resultSet.getString(column);
                    row.add(value == null ? "null" : value);
                }
                output.add(row.toString());
            }
            return output;
        }

        private static String formatHeader(ResultSetMetaData metadata, int columnCount)
                throws SQLException {
            StringJoiner header = new StringJoiner("\t");
            for (int column = 1; column <= columnCount; column++) {
                String label = metadata.getColumnLabel(column);
                header.add(label == null ? "" : label);
            }
            return header.toString();
        }
    }
}

package org.specdriven.agent.agent;

import com.lealone.orm.json.JsonObject;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link SessionStore} backed by two Lealone SQL tables.
 * <ul>
 *   <li>{@code agent_sessions} — structured columns per session</li>
 *   <li>{@code agent_messages} — one row per Message, content stored as JSON CLOB</li>
 * </ul>
 * Tables are auto-created on first initialization.
 * A background VirtualThread cleans up expired sessions every hour.
 */
public class LealoneSessionStore implements SessionStore {

    private static final System.Logger LOG =
            System.getLogger(LealoneSessionStore.class.getName());

    private static final String CREATE_SESSIONS = """
            CREATE TABLE IF NOT EXISTS agent_sessions (
                id         VARCHAR(36)  PRIMARY KEY,
                state      VARCHAR(20)  NOT NULL,
                created_at BIGINT       NOT NULL,
                updated_at BIGINT       NOT NULL,
                expiry_at  BIGINT       NOT NULL
            )
            """;

    private static final String CREATE_MESSAGES = """
            CREATE TABLE IF NOT EXISTS agent_messages (
                id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
                session_id VARCHAR(36)  NOT NULL,
                role       VARCHAR(20)  NOT NULL,
                content    CLOB         NOT NULL,
                tool_name  VARCHAR(255),
                ts         BIGINT       NOT NULL
            )
            """;

    private final String jdbcUrl;

    /**
     * Creates and initializes the store.
     * Tables are created if absent; background TTL cleanup is started.
     *
     * @param jdbcUrl Lealone JDBC URL, e.g. {@code jdbc:lealone:embed:agent_db}
     */
    public LealoneSessionStore(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        initTables();
        startCleanupThread();
    }

    // -------------------------------------------------------------------------
    // SessionStore implementation
    // -------------------------------------------------------------------------

    @Override
    public String save(Session session) {
        String id = session.id() != null ? session.id() : UUID.randomUUID().toString();
        long now = System.currentTimeMillis();

        String upsert = "MERGE INTO agent_sessions (id, state, created_at, updated_at, expiry_at) VALUES (?, ?, ?, ?, ?)";
        String deleteMessages = "DELETE FROM agent_messages WHERE session_id = ?";
        String insertMessage = "INSERT INTO agent_messages (session_id, role, content, tool_name, ts) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(upsert)) {
                ps.setString(1, id);
                ps.setString(2, session.state().name());
                ps.setLong(3, session.createdAt());
                ps.setLong(4, now);
                ps.setLong(5, session.expiryAt());
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(deleteMessages)) {
                ps.setString(1, id);
                ps.executeUpdate();
            }

            List<Message> messages = session.conversation() != null
                    ? session.conversation().history()
                    : Collections.emptyList();
            try (PreparedStatement ps = conn.prepareStatement(insertMessage)) {
                for (Message msg : messages) {
                    ps.setString(1, id);
                    ps.setString(2, msg.role());
                    ps.setString(3, messageToJson(msg));
                    ps.setString(4, msg instanceof ToolMessage tm ? tm.toolName() : null);
                    ps.setLong(5, msg.timestamp());
                    ps.addBatch();
                }
                if (!messages.isEmpty()) {
                    ps.executeBatch();
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save session " + id, e);
        }
        return id;
    }

    @Override
    public Optional<Session> load(String sessionId) {
        String selectSession = "SELECT state, created_at, updated_at, expiry_at FROM agent_sessions WHERE id = ?";
        String selectMessages = "SELECT content FROM agent_messages WHERE session_id = ? ORDER BY id";

        try (Connection conn = getConnection()) {
            Session session;
            try (PreparedStatement ps = conn.prepareStatement(selectSession)) {
                ps.setString(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    AgentState state = AgentState.valueOf(rs.getString("state"));
                    long createdAt = rs.getLong("created_at");
                    long updatedAt = rs.getLong("updated_at");
                    long expiryAt = rs.getLong("expiry_at");
                    session = new Session(sessionId, state, createdAt, updatedAt, expiryAt, new Conversation());
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(selectMessages)) {
                ps.setString(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Message msg = jsonToMessage(rs.getString("content"));
                        session.conversation().append(msg);
                    }
                }
            }
            return Optional.of(session);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load session " + sessionId, e);
        }
    }

    @Override
    public void delete(String sessionId) {
        String deleteMessages = "DELETE FROM agent_messages WHERE session_id = ?";
        String deleteSession = "DELETE FROM agent_sessions WHERE id = ?";

        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(deleteMessages)) {
                ps.setString(1, sessionId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(deleteSession)) {
                ps.setString(1, sessionId);
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete session " + sessionId, e);
        }
    }

    @Override
    public List<Session> listActive() {
        long now = System.currentTimeMillis();
        String selectActive = "SELECT id, state, created_at, updated_at, expiry_at FROM agent_sessions WHERE expiry_at > ?";
        String selectMessages = "SELECT content FROM agent_messages WHERE session_id = ? ORDER BY id";

        List<Session> result = new ArrayList<>();
        try (Connection conn = getConnection()) {
            List<Session> stubs = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(selectActive)) {
                ps.setLong(1, now);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        AgentState state = AgentState.valueOf(rs.getString("state"));
                        stubs.add(new Session(
                                rs.getString("id"),
                                state,
                                rs.getLong("created_at"),
                                rs.getLong("updated_at"),
                                rs.getLong("expiry_at"),
                                new Conversation()));
                    }
                }
            }

            for (Session stub : stubs) {
                try (PreparedStatement ps = conn.prepareStatement(selectMessages)) {
                    ps.setString(1, stub.id());
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            stub.conversation().append(jsonToMessage(rs.getString("content")));
                        }
                    }
                }
                result.add(stub);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list active sessions", e);
        }
        return Collections.unmodifiableList(result);
    }

    // -------------------------------------------------------------------------
    // Message serialization
    // -------------------------------------------------------------------------

    static String messageToJson(Message msg) {
        JsonObject json = new JsonObject();
        json.put("role", msg.role());
        json.put("content", msg.content());
        json.put("ts", msg.timestamp());
        if (msg instanceof ToolMessage tm) {
            json.put("toolName", tm.toolName());
            json.put("toolCallId", tm.toolCallId());
        }
        return json.encode();
    }

    static Message jsonToMessage(String jsonStr) {
        JsonObject json = new JsonObject(jsonStr);
        String role = json.getString("role");
        String content = json.getString("content");
        long ts = json.getLong("ts", 0L);
        return switch (role) {
            case "user" -> new UserMessage(content, ts);
            case "assistant" -> new AssistantMessage(content, ts);
            case "tool" -> new ToolMessage(content, ts,
                    json.getString("toolName"), json.getString("toolCallId"));
            case "system" -> new SystemMessage(content, ts);
            default -> throw new IllegalArgumentException("Unknown message role: " + role);
        };
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "root", "");
    }

    private void initTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_SESSIONS);
            stmt.execute(CREATE_MESSAGES);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize session tables", e);
        }
    }

    private void startCleanupThread() {
        Thread.ofVirtual().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(3_600_000L);
                    cleanupExpired();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.WARNING, "TTL cleanup failed", e);
                }
            }
        });
    }

    private void cleanupExpired() throws SQLException {
        long now = System.currentTimeMillis();
        String deleteMessages = """
                DELETE FROM agent_messages
                WHERE session_id IN (SELECT id FROM agent_sessions WHERE expiry_at < ?)
                """;
        String deleteSessions = "DELETE FROM agent_sessions WHERE expiry_at < ?";

        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(deleteMessages)) {
                ps.setLong(1, now);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(deleteSessions)) {
                ps.setLong(1, now);
                ps.executeUpdate();
            }
        }
    }
}

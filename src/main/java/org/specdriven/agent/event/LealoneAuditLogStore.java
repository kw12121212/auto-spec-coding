package org.specdriven.agent.event;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * {@link AuditLogStore} backed by a Lealone SQL table.
 * <p>
 * Auto-creates the {@code audit_log} table on initialization,
 * subscribes to all {@link EventType} values on the provided {@link EventBus},
 * and persists every published event. A background VirtualThread deletes
 * entries older than the configured retention period (default 30 days) every hour.
 */
public class LealoneAuditLogStore implements AuditLogStore {

    private static final System.Logger LOG =
            System.getLogger(LealoneAuditLogStore.class.getName());

    private static final long DEFAULT_RETENTION_MS = 30L * 24 * 60 * 60 * 1000;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS audit_log (
                id         BIGINT       PRIMARY KEY AUTO_INCREMENT,
                type       VARCHAR(50)  NOT NULL,
                source     VARCHAR(255) NOT NULL,
                event_ts   BIGINT       NOT NULL,
                event_json CLOB         NOT NULL
            )
            """;

    private final String jdbcUrl;
    private final long retentionMs;

    /**
     * Creates and initializes the store with default 30-day retention.
     *
     * @param eventBus the event bus to subscribe to
     * @param jdbcUrl  Lealone JDBC URL, e.g. {@code jdbc:lealone:embed:agent_db}
     */
    public LealoneAuditLogStore(EventBus eventBus, String jdbcUrl) {
        this(eventBus, jdbcUrl, DEFAULT_RETENTION_MS);
    }

    /**
     * Creates and initializes the store with custom retention.
     *
     * @param eventBus    the event bus to subscribe to
     * @param jdbcUrl     Lealone JDBC URL
     * @param retentionMs retention period in milliseconds
     */
    public LealoneAuditLogStore(EventBus eventBus, String jdbcUrl, long retentionMs) {
        this.jdbcUrl = jdbcUrl;
        this.retentionMs = retentionMs;
        initTable();
        subscribeAll(eventBus);
        startCleanupThread();
    }

    // -------------------------------------------------------------------------
    // AuditLogStore implementation
    // -------------------------------------------------------------------------

    @Override
    public long save(Event event) {
        String insert = "INSERT INTO audit_log (type, source, event_ts, event_json) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(insert, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, event.type().name());
            ps.setString(2, event.source());
            ps.setLong(3, event.timestamp());
            ps.setString(4, event.toJson());
            ps.executeUpdate();

            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
            return -1;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save audit entry", e);
        }
    }

    @Override
    public List<AuditEntry> query(EventType type, long fromTimestamp, long toTimestamp) {
        String sql = "SELECT id, event_json FROM audit_log WHERE type = ? AND event_ts >= ? AND event_ts <= ? ORDER BY event_ts ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, type.name());
            ps.setLong(2, fromTimestamp);
            ps.setLong(3, toTimestamp);
            return executeQuery(ps);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query audit log", e);
        }
    }

    @Override
    public List<AuditEntry> queryBySource(String source, long fromTimestamp, long toTimestamp) {
        String sql = "SELECT id, event_json FROM audit_log WHERE source = ? AND event_ts >= ? AND event_ts <= ? ORDER BY event_ts ASC";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, source);
            ps.setLong(2, fromTimestamp);
            ps.setLong(3, toTimestamp);
            return executeQuery(ps);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query audit log by source", e);
        }
    }

    @Override
    public int deleteOlderThan(long cutoffTimestamp) {
        String sql = "DELETE FROM audit_log WHERE event_ts < ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, cutoffTimestamp);
            return ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete old audit entries", e);
        }
    }

    @Override
    public long count() {
        String sql = "SELECT COUNT(*) FROM audit_log";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count audit entries", e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private List<AuditEntry> executeQuery(PreparedStatement ps) throws SQLException {
        List<AuditEntry> results = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String json = rs.getString("event_json");
                Event event = Event.fromJson(json);
                results.add(new AuditEntry(id, event));
            }
        }
        return Collections.unmodifiableList(results);
    }

    private void initTable() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize audit_log table", e);
        }
    }

    private void subscribeAll(EventBus eventBus) {
        Consumer<Event> listener = this::save;
        for (EventType type : EventType.values()) {
            eventBus.subscribe(type, listener);
        }
    }

    private void startCleanupThread() {
        Thread.ofVirtual().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(3_600_000L);
                    long cutoff = System.currentTimeMillis() - retentionMs;
                    deleteOlderThan(cutoff);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.WARNING, "Audit log TTL cleanup failed", e);
                }
            }
        });
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "root", "");
    }
}

package org.specdriven.agent.registry;

import com.lealone.orm.json.JsonObject;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.sql.*;
import java.util.*;

/**
 * {@link CronStore} backed by a Lealone SQL table.
 * Table is auto-created on first initialization.
 * A background VirtualThread polls for due entries every second and fires callbacks.
 * A separate cleanup VirtualThread removes CANCELLED entries older than 7 days every hour.
 */
public class LealoneCronStore implements CronStore {

    private static final System.Logger LOG =
            System.getLogger(LealoneCronStore.class.getName());

    private static final long CANCELLED_RETENTION_MS = 7L * 24 * 3600 * 1000;
    private static final long POLL_INTERVAL_MS = 1000L;
    private static final long CLEANUP_INTERVAL_MS = 3_600_000L;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS cron_entries (
                id              VARCHAR(36)  PRIMARY KEY,
                name            VARCHAR(500) NOT NULL,
                cron_expression VARCHAR(100),
                delay_millis    BIGINT       NOT NULL DEFAULT 0,
                status          VARCHAR(20)  NOT NULL,
                prompt          CLOB,
                metadata        CLOB,
                created_at      BIGINT       NOT NULL,
                updated_at      BIGINT       NOT NULL,
                next_fire_time  BIGINT       NOT NULL,
                last_fire_time  BIGINT       NOT NULL DEFAULT 0
            )
            """;

    private final String jdbcUrl;
    private final EventBus eventBus;
    private final Runnable callback;
    private volatile boolean stopped = false;

    /**
     * Creates and initializes the store.
     *
     * @param eventBus EventBus for publishing cron lifecycle events
     * @param jdbcUrl  Lealone JDBC URL, e.g. {@code jdbc:lealone:embed:agent_db}
     * @param callback the action to invoke when an entry fires
     */
    public LealoneCronStore(EventBus eventBus, String jdbcUrl, Runnable callback) {
        this.eventBus = eventBus;
        this.jdbcUrl = jdbcUrl;
        this.callback = callback;
        initTables();
        startSchedulerThread();
        startCleanupThread();
    }

    // -------------------------------------------------------------------------
    // CronStore implementation
    // -------------------------------------------------------------------------

    @Override
    public String create(CronEntry entry) {
        String id = entry.id() == null ? UUID.randomUUID().toString() : entry.id();
        long now = System.currentTimeMillis();

        long nextFire;
        if (entry.cronExpression() != null) {
            // Recurring: compute from cron expression
            nextFire = CronExpression.nextFireTime(entry.cronExpression(), now);
        } else {
            // One-shot: now + delayMillis
            nextFire = now + entry.delayMillis();
        }

        long createdAt = entry.id() == null ? now : entry.createdAt();

        String upsert = "MERGE INTO cron_entries (id, name, cron_expression, delay_millis, status, prompt, metadata, created_at, updated_at, next_fire_time, last_fire_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setString(1, id);
            ps.setString(2, entry.name());
            ps.setString(3, entry.cronExpression());
            ps.setLong(4, entry.delayMillis());
            ps.setString(5, CronStatus.ACTIVE.name());
            ps.setString(6, entry.prompt());
            ps.setString(7, mapToJson(entry.metadata()));
            ps.setLong(8, createdAt);
            ps.setLong(9, now);
            ps.setLong(10, nextFire);
            ps.setLong(11, entry.lastFireTime());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create cron entry " + id, e);
        }

        return id;
    }

    @Override
    public Optional<CronEntry> load(String entryId) {
        String select = "SELECT id, name, cron_expression, delay_millis, status, prompt, metadata, created_at, updated_at, next_fire_time, last_fire_time FROM cron_entries WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, entryId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(resultSetToEntry(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load cron entry " + entryId, e);
        }
    }

    @Override
    public void cancel(String entryId) {
        CronEntry existing = load(entryId).orElseThrow(() ->
                new NoSuchElementException("Cron entry not found: " + entryId));
        CronStatus.validateTransition(existing.status(), CronStatus.CANCELLED);

        long now = System.currentTimeMillis();
        String sql = "UPDATE cron_entries SET status = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, CronStatus.CANCELLED.name());
            ps.setLong(2, now);
            ps.setString(3, entryId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to cancel cron entry " + entryId, e);
        }
    }

    @Override
    public List<CronEntry> list() {
        return queryEntries("SELECT id, name, cron_expression, delay_millis, status, prompt, metadata, created_at, updated_at, next_fire_time, last_fire_time FROM cron_entries WHERE status <> ? ORDER BY created_at ASC",
                CronStatus.CANCELLED);
    }

    @Override
    public List<CronEntry> queryByStatus(CronStatus status) {
        return queryEntries("SELECT id, name, cron_expression, delay_millis, status, prompt, metadata, created_at, updated_at, next_fire_time, last_fire_time FROM cron_entries WHERE status = ? ORDER BY created_at ASC",
                status);
    }

    // -------------------------------------------------------------------------
    // Internal: scheduler
    // -------------------------------------------------------------------------

    private void startSchedulerThread() {
        Thread.ofVirtual().start(() -> {
            while (!stopped && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                    pollAndFire();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.WARNING, "Scheduler poll failed", e);
                }
            }
        });
    }

    private void pollAndFire() {
        long now = System.currentTimeMillis();
        String sql = "SELECT id, name, cron_expression, delay_millis, status, prompt, metadata, created_at, updated_at, next_fire_time, last_fire_time FROM cron_entries WHERE status = ? AND next_fire_time <= ?";

        List<CronEntry> due = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, CronStatus.ACTIVE.name());
            ps.setLong(2, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    due.add(resultSetToEntry(rs));
                }
            }
        } catch (SQLException e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to query due entries", e);
            return;
        }

        for (CronEntry entry : due) {
            try {
                fireEntry(entry, now);
            } catch (Exception e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Callback failed for entry " + entry.id() + ", will retry on next schedule", e);
            }
        }
    }

    private void fireEntry(CronEntry entry, long now) {
        // Invoke callback
        try {
            callback.run();
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING,
                    "Callback threw exception for entry " + entry.id(), e);
        }

        if (entry.cronExpression() != null) {
            // Recurring: compute next fire time, stay ACTIVE
            long nextFire = CronExpression.nextFireTime(entry.cronExpression(), now);
            String sql = "UPDATE cron_entries SET last_fire_time = ?, next_fire_time = ?, updated_at = ? WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setLong(1, now);
                ps.setLong(2, nextFire);
                ps.setLong(3, now);
                ps.setString(4, entry.id());
                ps.executeUpdate();
            } catch (SQLException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Failed to update next fire time for entry " + entry.id(), e);
            }
        } else {
            // One-shot: transition to FIRED
            String sql = "UPDATE cron_entries SET status = ?, last_fire_time = ?, updated_at = ? WHERE id = ?";
            try (Connection conn = getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, CronStatus.FIRED.name());
                ps.setLong(2, now);
                ps.setLong(3, now);
                ps.setString(4, entry.id());
                ps.executeUpdate();
            } catch (SQLException e) {
                LOG.log(System.Logger.Level.WARNING,
                        "Failed to mark entry as FIRED: " + entry.id(), e);
            }
        }

        publishEvent(entry);
    }

    // -------------------------------------------------------------------------
    // Internal: cleanup
    // -------------------------------------------------------------------------

    private void startCleanupThread() {
        Thread.ofVirtual().start(() -> {
            while (!stopped && !Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(CLEANUP_INTERVAL_MS);
                    cleanupCancelled();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.WARNING, "Cancelled-entry cleanup failed", e);
                }
            }
        });
    }

    private void cleanupCancelled() throws SQLException {
        long cutoff = System.currentTimeMillis() - CANCELLED_RETENTION_MS;
        String sql = "DELETE FROM cron_entries WHERE status = ? AND updated_at < ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, CronStatus.CANCELLED.name());
            ps.setLong(2, cutoff);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Internal: helpers
    // -------------------------------------------------------------------------

    private List<CronEntry> queryEntries(String sql, CronStatus filterStatus) {
        List<CronEntry> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filterStatus.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(resultSetToEntry(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query cron entries", e);
        }
        return Collections.unmodifiableList(result);
    }

    private CronEntry resultSetToEntry(ResultSet rs) throws SQLException {
        return new CronEntry(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("cron_expression"),
                rs.getLong("delay_millis"),
                CronStatus.valueOf(rs.getString("status")),
                rs.getString("prompt"),
                jsonToMap(rs.getString("metadata")),
                rs.getLong("created_at"),
                rs.getLong("updated_at"),
                rs.getLong("next_fire_time"),
                rs.getLong("last_fire_time"));
    }

    private void publishEvent(CronEntry entry) {
        try {
            eventBus.publish(new Event(EventType.CRON_TRIGGERED, System.currentTimeMillis(), "CronStore",
                    Map.of("entryId", entry.id(), "entryName", entry.name())));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to publish CRON_TRIGGERED event", e);
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
            throw new IllegalStateException("Failed to initialize cron_entries table", e);
        }
    }

    // -------------------------------------------------------------------------
    // Metadata serialization (same pattern as LealoneTaskStore)
    // -------------------------------------------------------------------------

    static String mapToJson(Map<String, Object> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        JsonObject json = new JsonObject();
        map.forEach((k, v) -> {
            if (v != null) {
                json.put(k, v);
            }
        });
        return json.encode();
    }

    static Map<String, Object> jsonToMap(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank() || "{}".equals(jsonStr.trim())) {
            return Map.of();
        }
        JsonObject json = new JsonObject(jsonStr);
        Map<String, Object> result = new LinkedHashMap<>();
        for (String key : json.fieldNames()) {
            Object value = json.getValue(key);
            if (value != null) {
                result.put(key, value);
            }
        }
        return Collections.unmodifiableMap(result);
    }
}

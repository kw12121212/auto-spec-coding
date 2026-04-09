package org.specdriven.agent.tool.cache;

import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.sql.*;
import java.util.Map;
import java.util.Optional;

/**
 * {@link ToolCache} backed by a Lealone embedded database.
 * Auto-creates {@code tool_cache} table on construction.
 * A background VirtualThread periodically deletes expired cache entries.
 */
public class LealoneToolCache implements ToolCache {

    private static final System.Logger LOG =
            System.getLogger(LealoneToolCache.class.getName());

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS tool_cache (
                cache_key     VARCHAR(64) PRIMARY KEY,
                tool_name     VARCHAR(255),
                output        CLOB,
                file_path     VARCHAR,
                file_modified BIGINT,
                created_at    BIGINT NOT NULL,
                ttl_ms        BIGINT NOT NULL,
                hit_count     BIGINT NOT NULL DEFAULT 0
            )
            """;

    private final String jdbcUrl;
    private final EventBus eventBus;

    /**
     * Creates and initializes the cache.
     *
     * @param eventBus EventBus for publishing cache hit/miss events
     * @param jdbcUrl  Lealone JDBC URL, e.g. {@code jdbc:lealone:embed:agent_db}
     */
    public LealoneToolCache(EventBus eventBus, String jdbcUrl) {
        this.eventBus = eventBus;
        this.jdbcUrl = jdbcUrl;
        initTable();
        startCleanupThread();
    }

    @Override
    public Optional<CacheEntry> get(String key) {
        String select = "SELECT output, created_at, ttl_ms, hit_count, file_path, file_modified FROM tool_cache WHERE cache_key = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    publishEvent(EventType.TOOL_CACHE_MISS, key);
                    return Optional.empty();
                }
                long createdAt = rs.getLong("created_at");
                long ttlMs = rs.getLong("ttl_ms");
                if (System.currentTimeMillis() > createdAt + ttlMs) {
                    deleteByKey(key);
                    publishEvent(EventType.TOOL_CACHE_MISS, key);
                    return Optional.empty();
                }
                String output = rs.getString("output");
                String filePath = rs.getString("file_path");
                long fileModified = rs.getLong("file_modified");
                incrementHitCount(key);
                publishEvent(EventType.TOOL_CACHE_HIT, key);
                return Optional.of(new CacheEntry(output, filePath, fileModified));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to get cache entry: " + key, e);
        }
    }

    @Override
    public void put(String key, String output, long ttlMs, String filePath, long fileModified) {
        String upsert = "MERGE INTO tool_cache (cache_key, tool_name, output, file_path, file_modified, created_at, ttl_ms, hit_count) VALUES (?, '', ?, ?, ?, ?, ?, 0)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setString(1, key);
            ps.setString(2, output);
            ps.setString(3, filePath);
            ps.setLong(4, fileModified);
            ps.setLong(5, System.currentTimeMillis());
            ps.setLong(6, ttlMs);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to put cache entry: " + key, e);
        }
    }

    @Override
    public void invalidate(String key) {
        deleteByKey(key);
    }

    @Override
    public void clear() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM tool_cache");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clear cache", e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void deleteByKey(String key) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM tool_cache WHERE cache_key = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete cache entry: " + key, e);
        }
    }

    private void incrementHitCount(String key) {
        String sql = "UPDATE tool_cache SET hit_count = hit_count + 1 WHERE cache_key = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to increment hit count for " + key, e);
        }
    }

    private void publishEvent(EventType type, String cacheKey) {
        try {
            eventBus.publish(new Event(type, System.currentTimeMillis(), "ToolCache",
                    Map.of("cacheKey", cacheKey)));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to publish event " + type, e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "root", "");
    }

    private void initTable() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize tool_cache table", e);
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
                    LOG.log(System.Logger.Level.WARNING, "Tool cache cleanup failed", e);
                }
            }
        });
    }

    private void cleanupExpired() throws SQLException {
        long now = System.currentTimeMillis();
        String sql = "DELETE FROM tool_cache WHERE created_at + ttl_ms < ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, now);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                LOG.log(System.Logger.Level.INFO, "Cleaned up " + deleted + " expired tool cache entries");
            }
        }
    }
}

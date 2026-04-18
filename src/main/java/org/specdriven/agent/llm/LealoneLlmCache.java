package org.specdriven.agent.llm;

import org.specdriven.agent.agent.LlmUsage;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.sql.*;
import java.util.*;
import java.util.function.LongSupplier;

/**
 * {@link LlmCache} backed by a Lealone embedded database.
 * Auto-creates {@code llm_cache} and {@code llm_usage} tables on construction.
 * A background VirtualThread periodically deletes expired cache entries.
 */
public class LealoneLlmCache implements LlmCache {

    private static final System.Logger LOG =
            System.getLogger(LealoneLlmCache.class.getName());

    private static final String CREATE_CACHE_TABLE = """
            CREATE TABLE IF NOT EXISTS llm_cache (
                cache_key     VARCHAR(64) PRIMARY KEY,
                response_json CLOB,
                created_at    BIGINT NOT NULL,
                ttl_ms        BIGINT NOT NULL,
                hit_count     BIGINT NOT NULL DEFAULT 0
            )
            """;

    private static final String CREATE_USAGE_TABLE = """
            CREATE TABLE IF NOT EXISTS llm_usage (
                id                 BIGINT PRIMARY KEY AUTO_INCREMENT,
                session_id         VARCHAR(255),
                agent_name         VARCHAR(255),
                model              VARCHAR(255),
                prompt_tokens      INT NOT NULL,
                completion_tokens  INT NOT NULL,
                total_tokens       INT NOT NULL,
                created_at         BIGINT NOT NULL
            )
            """;

    private final String jdbcUrl;
    private final EventBus eventBus;
    private final LongSupplier clock;

    /**
     * Creates and initializes the cache.
     *
     * @param eventBus EventBus for publishing cache hit/miss events
     * @param jdbcUrl  Lealone JDBC URL, e.g. {@code jdbc:lealone:embed:agent_db}
     */
    public LealoneLlmCache(EventBus eventBus, String jdbcUrl) {
        this(eventBus, jdbcUrl, System::currentTimeMillis);
    }

    /** Constructor with injectable clock for testing. */
    LealoneLlmCache(EventBus eventBus, String jdbcUrl, LongSupplier clock) {
        this.eventBus = eventBus;
        this.jdbcUrl = jdbcUrl;
        this.clock = clock;
        initTables();
        startCleanupThread();
    }

    // -------------------------------------------------------------------------
    // LlmCache — cache operations
    // -------------------------------------------------------------------------

    @Override
    public Optional<String> get(String key) {
        // First check if entry exists and is not expired
        String select = "SELECT response_json, created_at, ttl_ms, hit_count FROM llm_cache WHERE cache_key = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    publishEvent(EventType.LLM_CACHE_MISS, key);
                    return Optional.empty();
                }
                long createdAt = rs.getLong("created_at");
                long ttlMs = rs.getLong("ttl_ms");
                if (clock.getAsLong() > createdAt + ttlMs) {
                    // Expired — delete and return miss
                    deleteByKey(key);
                    publishEvent(EventType.LLM_CACHE_MISS, key);
                    return Optional.empty();
                }
                String responseJson = rs.getString("response_json");
                incrementHitCount(key);
                publishEvent(EventType.LLM_CACHE_HIT, key);
                return Optional.ofNullable(responseJson);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to get cache entry: " + key, e);
        }
    }

    @Override
    public void put(String key, String responseJson, long ttlMs) {
        String upsert = "MERGE INTO llm_cache (cache_key, response_json, created_at, ttl_ms, hit_count) VALUES (?, ?, ?, ?, 0)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setString(1, key);
            ps.setString(2, responseJson);
            ps.setLong(3, clock.getAsLong());
            ps.setLong(4, ttlMs);
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
            stmt.execute("DELETE FROM llm_cache");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clear cache", e);
        }
    }

    // -------------------------------------------------------------------------
    // LlmCache — usage operations
    // -------------------------------------------------------------------------

    @Override
    public void recordUsage(String sessionId, String agentName, String model, LlmUsage usage) {
        String insert = "INSERT INTO llm_usage (session_id, agent_name, model, prompt_tokens, completion_tokens, total_tokens, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setString(1, sessionId);
            ps.setString(2, agentName);
            ps.setString(3, model);
            ps.setInt(4, usage.promptTokens());
            ps.setInt(5, usage.completionTokens());
            ps.setInt(6, usage.totalTokens());
            ps.setLong(7, clock.getAsLong());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to record usage", e);
        }
    }

    @Override
    public List<UsageRecord> queryUsage(String sessionId, String agentName, long from, long to) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, session_id, agent_name, model, prompt_tokens, completion_tokens, total_tokens, created_at FROM llm_usage WHERE created_at >= ? AND created_at <= ?");
        List<Object> params = new ArrayList<>();
        params.add(from);
        params.add(to);

        if (sessionId != null) {
            sql.append(" AND session_id = ?");
            params.add(sessionId);
        }
        if (agentName != null) {
            sql.append(" AND agent_name = ?");
            params.add(agentName);
        }
        sql.append(" ORDER BY created_at ASC");

        List<UsageRecord> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                Object p = params.get(i);
                if (p instanceof String s) {
                    ps.setString(i + 1, s);
                } else if (p instanceof Long l) {
                    ps.setLong(i + 1, l);
                }
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new UsageRecord(
                            rs.getLong("id"),
                            rs.getString("session_id"),
                            rs.getString("agent_name"),
                            rs.getString("model"),
                            rs.getInt("prompt_tokens"),
                            rs.getInt("completion_tokens"),
                            rs.getInt("total_tokens"),
                            rs.getLong("created_at")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query usage", e);
        }
        return Collections.unmodifiableList(result);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void deleteByKey(String key) {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM llm_cache WHERE cache_key = ?")) {
            ps.setString(1, key);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to delete cache entry: " + key, e);
        }
    }

    private void incrementHitCount(String key) {
        String sql = "UPDATE llm_cache SET hit_count = hit_count + 1 WHERE cache_key = ?";
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
            eventBus.publish(new Event(type, System.currentTimeMillis(), "LlmCache",
                    Map.of("cacheKey", cacheKey)));
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
            stmt.execute(CREATE_CACHE_TABLE);
            stmt.execute(CREATE_USAGE_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize cache tables", e);
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
                    LOG.log(System.Logger.Level.WARNING, "Cache cleanup failed", e);
                }
            }
        });
    }

    private void cleanupExpired() throws SQLException {
        long now = clock.getAsLong();
        String sql = "DELETE FROM llm_cache WHERE created_at + ttl_ms < ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, now);
            int deleted = ps.executeUpdate();
            if (deleted > 0) {
                LOG.log(System.Logger.Level.INFO, "Cleaned up " + deleted + " expired cache entries");
            }
        }
    }
}

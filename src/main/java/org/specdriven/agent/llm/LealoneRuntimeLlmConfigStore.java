package org.specdriven.agent.llm;

import org.specdriven.agent.agent.LlmConfigSnapshot;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Lealone-backed store for persisted default runtime LLM config snapshots.
 */
public class LealoneRuntimeLlmConfigStore implements RuntimeLlmConfigStore {

    private static final String DEFAULT_SCOPE = "default";

    private static final String CREATE_VERSIONS_TABLE = """
            CREATE TABLE IF NOT EXISTS llm_runtime_config_versions (
                version       BIGINT PRIMARY KEY,
                provider_name VARCHAR(255) NOT NULL,
                base_url      VARCHAR(1024) NOT NULL,
                model         VARCHAR(255) NOT NULL,
                timeout       INT NOT NULL,
                max_retries   INT NOT NULL,
                persisted_at  BIGINT NOT NULL
            )
            """;

    private static final String CREATE_ACTIVE_TABLE = """
            CREATE TABLE IF NOT EXISTS llm_runtime_config_active (
                scope_id      VARCHAR(32) PRIMARY KEY,
                active_version BIGINT NOT NULL,
                updated_at    BIGINT NOT NULL
            )
            """;

    private static final String SELECT_ACTIVE_SNAPSHOT = """
            SELECT v.version, v.provider_name, v.base_url, v.model, v.timeout, v.max_retries, v.persisted_at
            FROM llm_runtime_config_active a
            JOIN llm_runtime_config_versions v ON v.version = a.active_version
            WHERE a.scope_id = ?
            """;

    private static final String SELECT_HISTORY = """
            SELECT v.version,
                   v.provider_name,
                   v.base_url,
                   v.model,
                   v.timeout,
                   v.max_retries,
                   v.persisted_at,
                   CASE WHEN a.active_version = v.version THEN TRUE ELSE FALSE END AS active
            FROM llm_runtime_config_versions v
            LEFT JOIN llm_runtime_config_active a ON a.scope_id = ?
            ORDER BY v.version DESC
            """;

    private static final String SELECT_VERSION = """
            SELECT version, provider_name, base_url, model, timeout, max_retries, persisted_at
            FROM llm_runtime_config_versions
            WHERE version = ?
            """;

    private final String jdbcUrl;
    private final Runnable beforeActivateHook;

    public LealoneRuntimeLlmConfigStore(String jdbcUrl) {
        this(jdbcUrl, () -> {
        });
    }

    LealoneRuntimeLlmConfigStore(String jdbcUrl, Runnable beforeActivateHook) {
        this.jdbcUrl = Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        this.beforeActivateHook = Objects.requireNonNull(beforeActivateHook, "beforeActivateHook must not be null");
        initTables();
    }

    @Override
    public Optional<LlmConfigSnapshot> loadDefaultSnapshot() {
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_ACTIVE_SNAPSHOT)) {
            ps.setString(1, DEFAULT_SCOPE);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(readSnapshot(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load persisted default runtime snapshot", e);
        }
    }

    @Override
    public RuntimeLlmConfigVersion persistDefaultSnapshot(LlmConfigSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot must not be null");
        long persistedAt = System.currentTimeMillis();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                long version = nextVersion(conn);
                insertVersion(conn, version, snapshot, persistedAt);
                beforeActivateHook.run();
                activateVersion(conn, version, persistedAt);
                conn.commit();
                return new RuntimeLlmConfigVersion(version, persistedAt, snapshot, true);
            } catch (Exception e) {
                rollbackQuietly(conn);
                throw new IllegalStateException("Failed to persist default runtime snapshot", e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to persist default runtime snapshot", e);
        }
    }

    @Override
    public List<RuntimeLlmConfigVersion> listDefaultSnapshotVersions() {
        List<RuntimeLlmConfigVersion> versions = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_HISTORY)) {
            ps.setString(1, DEFAULT_SCOPE);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    versions.add(new RuntimeLlmConfigVersion(
                            rs.getLong("version"),
                            rs.getLong("persisted_at"),
                            readSnapshot(rs),
                            rs.getBoolean("active")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list persisted default runtime snapshots", e);
        }
        return List.copyOf(versions);
    }

    @Override
    public RuntimeLlmConfigVersion restoreDefaultSnapshot(long version) {
        long updatedAt = System.currentTimeMillis();
        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false);
            try {
                RuntimeLlmConfigVersion restored = loadVersion(conn, version)
                        .orElseThrow(() -> new IllegalArgumentException("No persisted default snapshot version " + version));
                activateVersion(conn, version, updatedAt);
                conn.commit();
                return new RuntimeLlmConfigVersion(restored.version(), restored.persistedAt(), restored.snapshot(), true);
            } catch (Exception e) {
                rollbackQuietly(conn);
                if (e instanceof IllegalArgumentException illegalArgumentException) {
                    throw illegalArgumentException;
                }
                throw new IllegalStateException("Failed to restore persisted default runtime snapshot version " + version, e);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to restore persisted default runtime snapshot version " + version, e);
        }
    }

    private void initTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_VERSIONS_TABLE);
            stmt.execute(CREATE_ACTIVE_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize runtime LLM config tables", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "root", "");
    }

    private long nextVersion(Connection conn) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(MAX(version), 0) + 1 AS next_version FROM llm_runtime_config_versions");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong("next_version");
        }
    }

    private void insertVersion(Connection conn, long version, LlmConfigSnapshot snapshot, long persistedAt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                INSERT INTO llm_runtime_config_versions
                    (version, provider_name, base_url, model, timeout, max_retries, persisted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            ps.setLong(1, version);
            ps.setString(2, snapshot.providerName());
            ps.setString(3, snapshot.baseUrl());
            ps.setString(4, snapshot.model());
            ps.setInt(5, snapshot.timeout());
            ps.setInt(6, snapshot.maxRetries());
            ps.setLong(7, persistedAt);
            ps.executeUpdate();
        }
    }

    private void activateVersion(Connection conn, long version, long updatedAt) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement("""
                MERGE INTO llm_runtime_config_active (scope_id, active_version, updated_at)
                KEY(scope_id)
                VALUES (?, ?, ?)
                """)) {
            ps.setString(1, DEFAULT_SCOPE);
            ps.setLong(2, version);
            ps.setLong(3, updatedAt);
            ps.executeUpdate();
        }
    }

    private Optional<RuntimeLlmConfigVersion> loadVersion(Connection conn, long version) throws SQLException {
        try (PreparedStatement ps = conn.prepareStatement(SELECT_VERSION)) {
            ps.setLong(1, version);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new RuntimeLlmConfigVersion(
                        rs.getLong("version"),
                        rs.getLong("persisted_at"),
                        readSnapshot(rs),
                        false));
            }
        }
    }

    private LlmConfigSnapshot readSnapshot(ResultSet rs) throws SQLException {
        return new LlmConfigSnapshot(
                rs.getString("provider_name"),
                rs.getString("base_url"),
                rs.getString("model"),
                rs.getInt("timeout"),
                rs.getInt("max_retries"));
    }

    private void rollbackQuietly(Connection conn) {
        try {
            conn.rollback();
        } catch (SQLException ignored) {
        }
    }
}

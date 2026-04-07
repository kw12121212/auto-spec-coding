package org.specdriven.agent.permission;

import com.lealone.orm.json.JsonObject;

import java.sql.*;
import java.util.*;

/**
 * {@link PolicyStore} backed by two Lealone SQL tables.
 * <ul>
 *   <li>{@code permission_policies} — active grant/deny overrides</li>
 *   <li>{@code permission_audit_log} — append-only grant/revoke trail</li>
 * </ul>
 * Tables are auto-created on first initialization.
 */
public class LealonePolicyStore implements PolicyStore {

    private static final String CREATE_POLICIES = """
            CREATE TABLE IF NOT EXISTS permission_policies (
                id         VARCHAR(36)  PRIMARY KEY,
                action     VARCHAR(255) NOT NULL,
                resource   VARCHAR(512) NOT NULL,
                decision   VARCHAR(20)  NOT NULL,
                constraints CLOB,
                requester  VARCHAR(255) NOT NULL,
                created_at BIGINT       NOT NULL,
                updated_at BIGINT       NOT NULL
            )
            """;

    private static final String CREATE_AUDIT_LOG = """
            CREATE TABLE IF NOT EXISTS permission_audit_log (
                id           VARCHAR(36)  PRIMARY KEY,
                operation    VARCHAR(20)  NOT NULL,
                action       VARCHAR(255) NOT NULL,
                resource     VARCHAR(512) NOT NULL,
                requester    VARCHAR(255) NOT NULL,
                performed_by VARCHAR(255) NOT NULL,
                ts           BIGINT       NOT NULL,
                metadata     CLOB
            )
            """;

    private final String jdbcUrl;

    public LealonePolicyStore(String jdbcUrl) {
        this.jdbcUrl = jdbcUrl;
        initTables();
    }

    @Override
    public void grant(Permission permission, PermissionContext context) {
        long now = System.currentTimeMillis();
        String id = UUID.randomUUID().toString();
        String constraintsJson = mapToJson(permission.constraints());

        String upsert = """
                MERGE INTO permission_policies (id, action, resource, decision, constraints, requester, created_at, updated_at)
                KEY(action, resource, requester)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setString(1, id);
            ps.setString(2, permission.action());
            ps.setString(3, permission.resource());
            ps.setString(4, PermissionDecision.ALLOW.name());
            ps.setString(5, constraintsJson);
            ps.setString(6, context.requester());
            ps.setLong(7, now);
            ps.setLong(8, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to grant permission", e);
        }

        insertAuditEntry("GRANT", permission, context, now);
    }

    @Override
    public void revoke(Permission permission, PermissionContext context) {
        long now = System.currentTimeMillis();

        String delete = "DELETE FROM permission_policies WHERE action = ? AND resource = ? AND requester = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(delete)) {
            ps.setString(1, permission.action());
            ps.setString(2, permission.resource());
            ps.setString(3, context.requester());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to revoke permission", e);
        }

        insertAuditEntry("REVOKE", permission, context, now);
    }

    @Override
    public Optional<PermissionDecision> find(Permission permission, PermissionContext context) {
        String select = "SELECT decision FROM permission_policies WHERE action = ? AND resource = ? AND requester = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, permission.action());
            ps.setString(2, permission.resource());
            ps.setString(3, context.requester());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(PermissionDecision.valueOf(rs.getString("decision")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to find permission policy", e);
        }
        return Optional.empty();
    }

    @Override
    public List<StoredPolicy> listPolicies() {
        String select = "SELECT id, action, resource, decision, constraints, requester, created_at, updated_at FROM permission_policies ORDER BY updated_at DESC";
        List<StoredPolicy> result = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(select)) {
            while (rs.next()) {
                Map<String, String> constraints = jsonToMap(rs.getString("constraints"));
                Permission permission = new Permission(
                        rs.getString("action"),
                        rs.getString("resource"),
                        constraints);
                result.add(new StoredPolicy(
                        rs.getString("id"),
                        permission,
                        PermissionDecision.valueOf(rs.getString("decision")),
                        rs.getLong("created_at"),
                        rs.getLong("updated_at")));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list policies", e);
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public List<AuditEntry> auditLog() {
        String select = "SELECT id, operation, action, resource, requester, performed_by, ts, metadata FROM permission_audit_log ORDER BY ts DESC";
        List<AuditEntry> result = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(select)) {
            while (rs.next()) {
                result.add(new AuditEntry(
                        rs.getString("id"),
                        rs.getString("operation"),
                        rs.getString("action"),
                        rs.getString("resource"),
                        rs.getString("requester"),
                        rs.getString("performed_by"),
                        rs.getLong("ts"),
                        jsonToMap(rs.getString("metadata"))));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read audit log", e);
        }
        return Collections.unmodifiableList(result);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void insertAuditEntry(String operation, Permission permission, PermissionContext context, long timestamp) {
        String insert = """
                INSERT INTO permission_audit_log (id, operation, action, resource, requester, performed_by, ts, metadata)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setString(1, UUID.randomUUID().toString());
            ps.setString(2, operation);
            ps.setString(3, permission.action());
            ps.setString(4, permission.resource());
            ps.setString(5, context.requester());
            ps.setString(6, context.requester());
            ps.setLong(7, timestamp);
            ps.setString(8, mapToJson(permission.constraints()));
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to insert audit entry", e);
        }
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "root", "");
    }

    private void initTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_POLICIES);
            stmt.execute(CREATE_AUDIT_LOG);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize permission tables", e);
        }
    }

    static String mapToJson(Map<String, String> map) {
        if (map == null || map.isEmpty()) {
            return "{}";
        }
        JsonObject json = new JsonObject();
        map.forEach(json::put);
        return json.encode();
    }

    static Map<String, String> jsonToMap(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank() || "{}".equals(jsonStr.trim())) {
            return Map.of();
        }
        JsonObject json = new JsonObject(jsonStr);
        Map<String, String> result = new LinkedHashMap<>();
        for (String key : json.fieldNames()) {
            Object value = json.getValue(key);
            if (value != null) {
                result.put(key, value.toString());
            }
        }
        return Collections.unmodifiableMap(result);
    }
}

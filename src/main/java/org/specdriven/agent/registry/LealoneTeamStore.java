package org.specdriven.agent.registry;

import com.lealone.orm.json.JsonObject;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.sql.*;
import java.util.*;

/**
 * {@link TeamStore} backed by Lealone SQL tables.
 * Tables are auto-created on first initialization.
 * A background VirtualThread cleans up DISSOLVED teams older than 7 days every hour.
 */
public class LealoneTeamStore implements TeamStore {

    private static final System.Logger LOG =
            System.getLogger(LealoneTeamStore.class.getName());

    private static final long DISSOLVED_RETENTION_MS = 7L * 24 * 3600 * 1000;

    private static final String CREATE_TEAMS_TABLE = """
            CREATE TABLE IF NOT EXISTS teams (
                id          VARCHAR(36)  PRIMARY KEY,
                name        VARCHAR(255) NOT NULL,
                description CLOB,
                status      VARCHAR(20)  NOT NULL,
                metadata    CLOB,
                created_at  BIGINT       NOT NULL,
                updated_at  BIGINT       NOT NULL
            )
            """;

    private static final String CREATE_MEMBERS_TABLE = """
            CREATE TABLE IF NOT EXISTS team_members (
                team_id     VARCHAR(36)  NOT NULL,
                member_id   VARCHAR(255) NOT NULL,
                role        VARCHAR(20)  NOT NULL,
                joined_at   BIGINT       NOT NULL,
                PRIMARY KEY (team_id, member_id)
            )
            """;

    private final String jdbcUrl;
    private final EventBus eventBus;

    /**
     * Creates and initializes the store.
     *
     * @param eventBus EventBus for publishing team lifecycle events
     * @param jdbcUrl  Lealone JDBC URL, e.g. {@code jdbc:lealone:embed:agent_db}
     */
    public LealoneTeamStore(EventBus eventBus, String jdbcUrl) {
        this.eventBus = eventBus;
        this.jdbcUrl = jdbcUrl;
        initTables();
        startCleanupThread();
    }

    // -------------------------------------------------------------------------
    // TeamStore — team operations
    // -------------------------------------------------------------------------

    @Override
    public String create(Team team) {
        boolean isNew = team.id() == null;
        String id = isNew ? UUID.randomUUID().toString() : team.id();
        long now = System.currentTimeMillis();
        long createdAt = isNew ? now : team.createdAt();

        String upsert = "MERGE INTO teams (id, name, description, status, metadata, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setString(1, id);
            ps.setString(2, team.name());
            ps.setString(3, team.description());
            ps.setString(4, team.status().name());
            ps.setString(5, LealoneTaskStore.mapToJson(team.metadata()));
            ps.setLong(6, createdAt);
            ps.setLong(7, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save team " + id, e);
        }

        if (isNew) {
            publishEvent(EventType.TEAM_CREATED, id);
        }

        return id;
    }

    @Override
    public Optional<Team> load(String teamId) {
        String select = "SELECT id, name, description, status, metadata, created_at, updated_at FROM teams WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(resultSetToTeam(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load team " + teamId, e);
        }
    }

    @Override
    public Team update(String teamId, String name, String description) {
        Team existing = load(teamId).orElseThrow(() ->
                new NoSuchElementException("Team not found: " + teamId));

        long now = System.currentTimeMillis();
        String sql = "UPDATE teams SET name = ?, description = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, description);
            ps.setLong(3, now);
            ps.setString(4, teamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update team " + teamId, e);
        }

        return new Team(teamId, name, description, existing.status(),
                existing.metadata(), existing.createdAt(), now);
    }

    @Override
    public void dissolve(String teamId) {
        Team existing = load(teamId).orElseThrow(() ->
                new NoSuchElementException("Team not found: " + teamId));
        TeamStatus.validateTransition(existing.status(), TeamStatus.DISSOLVED);

        long now = System.currentTimeMillis();
        String sql = "UPDATE teams SET status = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, TeamStatus.DISSOLVED.name());
            ps.setLong(2, now);
            ps.setString(3, teamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to dissolve team " + teamId, e);
        }

        // Remove all members
        String deleteMembers = "DELETE FROM team_members WHERE team_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(deleteMembers)) {
            ps.setString(1, teamId);
            ps.executeUpdate();
        } catch (SQLException e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to remove members for dissolved team " + teamId, e);
        }

        publishEvent(EventType.TEAM_DISSOLVED, teamId);
    }

    @Override
    public List<Team> list() {
        String sql = "SELECT id, name, description, status, metadata, created_at, updated_at FROM teams WHERE status <> ? ORDER BY created_at ASC";
        List<Team> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, TeamStatus.DISSOLVED.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(resultSetToTeam(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list teams", e);
        }
        return Collections.unmodifiableList(result);
    }

    // -------------------------------------------------------------------------
    // TeamStore — member operations
    // -------------------------------------------------------------------------

    @Override
    public void joinTeam(String teamId, String memberId, TeamRole role) {
        Team team = load(teamId).orElseThrow(() ->
                new NoSuchElementException("Team not found: " + teamId));
        if (team.status() == TeamStatus.DISSOLVED) {
            throw new IllegalStateException("Cannot join a dissolved team: " + teamId);
        }

        String insert = "INSERT INTO team_members (team_id, member_id, role, joined_at) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(insert)) {
            ps.setString(1, teamId);
            ps.setString(2, memberId);
            ps.setString(3, role.name());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Member " + memberId + " is already in team " + teamId, e);
        }
    }

    @Override
    public void leaveTeam(String teamId, String memberId) {
        Team team = load(teamId).orElseThrow(() ->
                new NoSuchElementException("Team not found: " + teamId));
        if (team.status() == TeamStatus.DISSOLVED) {
            throw new IllegalStateException("Cannot leave a dissolved team: " + teamId);
        }

        String delete = "DELETE FROM team_members WHERE team_id = ? AND member_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(delete)) {
            ps.setString(1, teamId);
            ps.setString(2, memberId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new NoSuchElementException("Member " + memberId + " not found in team " + teamId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to remove member " + memberId + " from team " + teamId, e);
        }
    }

    @Override
    public void updateRole(String teamId, String memberId, TeamRole newRole) {
        load(teamId).orElseThrow(() ->
                new NoSuchElementException("Team not found: " + teamId));

        String update = "UPDATE team_members SET role = ? WHERE team_id = ? AND member_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(update)) {
            ps.setString(1, newRole.name());
            ps.setString(2, teamId);
            ps.setString(3, memberId);
            int rows = ps.executeUpdate();
            if (rows == 0) {
                throw new NoSuchElementException("Member " + memberId + " not found in team " + teamId);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update role for member " + memberId, e);
        }
    }

    @Override
    public List<TeamMember> listMembers(String teamId) {
        Team team = load(teamId).orElse(null);
        if (team == null || team.status() == TeamStatus.DISSOLVED) {
            return List.of();
        }

        String sql = "SELECT team_id, member_id, role, joined_at FROM team_members WHERE team_id = ? ORDER BY joined_at ASC";
        List<TeamMember> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, teamId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(new TeamMember(
                            rs.getString("team_id"),
                            rs.getString("member_id"),
                            TeamRole.valueOf(rs.getString("role")),
                            rs.getLong("joined_at")));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to list members for team " + teamId, e);
        }
        return Collections.unmodifiableList(result);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private Team resultSetToTeam(ResultSet rs) throws SQLException {
        return new Team(
                rs.getString("id"),
                rs.getString("name"),
                rs.getString("description"),
                TeamStatus.valueOf(rs.getString("status")),
                LealoneTaskStore.jsonToMap(rs.getString("metadata")),
                rs.getLong("created_at"),
                rs.getLong("updated_at"));
    }

    private void publishEvent(EventType type, String teamId) {
        try {
            eventBus.publish(new Event(type, System.currentTimeMillis(), "TeamStore",
                    Map.of("teamId", teamId)));
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
            stmt.execute(CREATE_TEAMS_TABLE);
            stmt.execute(CREATE_MEMBERS_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize teams tables", e);
        }
    }

    private void startCleanupThread() {
        Thread.ofVirtual().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(3_600_000L);
                    cleanupDissolved();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.WARNING, "Dissolved-team cleanup failed", e);
                }
            }
        });
    }

    private void cleanupDissolved() throws SQLException {
        long cutoff = System.currentTimeMillis() - DISSOLVED_RETENTION_MS;
        // Delete members first, then teams
        String deleteMembers = "DELETE FROM team_members WHERE team_id IN (SELECT id FROM teams WHERE status = ? AND updated_at < ?)";
        String deleteTeams = "DELETE FROM teams WHERE status = ? AND updated_at < ?";
        try (Connection conn = getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(deleteMembers)) {
                ps.setString(1, TeamStatus.DISSOLVED.name());
                ps.setLong(2, cutoff);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(deleteTeams)) {
                ps.setString(1, TeamStatus.DISSOLVED.name());
                ps.setLong(2, cutoff);
                ps.executeUpdate();
            }
        }
    }
}

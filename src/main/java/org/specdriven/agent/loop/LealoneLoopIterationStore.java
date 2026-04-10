package org.specdriven.agent.loop;

import com.lealone.orm.json.JsonArray;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.sql.*;
import java.util.*;

/**
 * {@link LoopIterationStore} backed by Lealone SQL tables.
 * Tables are auto-created on construction.
 */
public class LealoneLoopIterationStore implements LoopIterationStore {

    private static final System.Logger LOG =
            System.getLogger(LealoneLoopIterationStore.class.getName());

    private static final String CREATE_ITERATIONS_TABLE = """
            CREATE TABLE IF NOT EXISTS loop_iterations (
                iteration_number INT    PRIMARY KEY,
                change_name      VARCHAR(500) NOT NULL,
                milestone_file   VARCHAR(500) NOT NULL,
                started_at       BIGINT       NOT NULL,
                completed_at     BIGINT,
                status           VARCHAR(20)  NOT NULL,
                failure_reason   CLOB
            )
            """;

    private static final String CREATE_PROGRESS_TABLE = """
            CREATE TABLE IF NOT EXISTS loop_progress (
                id                     INT    PRIMARY KEY,
                loop_state             VARCHAR(20) NOT NULL,
                completed_change_names CLOB,
                total_iterations       INT    NOT NULL DEFAULT 0,
                updated_at             BIGINT NOT NULL
            )
            """;

    private final String jdbcUrl;
    private final EventBus eventBus;

    /**
     * Creates and initializes the store.
     *
     * @param eventBus EventBus for publishing progress events
     * @param jdbcUrl  Lealone JDBC URL, e.g. {@code jdbc:lealone:embed:agent_db}
     */
    public LealoneLoopIterationStore(EventBus eventBus, String jdbcUrl) {
        this.eventBus = eventBus;
        this.jdbcUrl = jdbcUrl;
        initTables();
    }

    @Override
    public void saveIteration(LoopIteration iteration) {
        String sql = "MERGE INTO loop_iterations (iteration_number, change_name, milestone_file, started_at, completed_at, status, failure_reason) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, iteration.iterationNumber());
            ps.setString(2, iteration.changeName());
            ps.setString(3, iteration.milestoneFile());
            ps.setLong(4, iteration.startedAt());
            if (iteration.completedAt() != null) {
                ps.setLong(5, iteration.completedAt());
            } else {
                ps.setNull(5, Types.BIGINT);
            }
            ps.setString(6, iteration.status().name());
            ps.setString(7, iteration.failureReason());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save iteration " + iteration.iterationNumber(), e);
        }
    }

    @Override
    public List<LoopIteration> loadIterations() {
        String sql = "SELECT iteration_number, change_name, milestone_file, started_at, completed_at, status, failure_reason FROM loop_iterations ORDER BY iteration_number ASC";
        List<LoopIteration> result = new ArrayList<>();
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                result.add(resultSetToIteration(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load iterations", e);
        }
        return Collections.unmodifiableList(result);
    }

    @Override
    public void saveProgress(LoopProgress progress) {
        String sql = "MERGE INTO loop_progress (id, loop_state, completed_change_names, total_iterations, updated_at) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, 1);
            ps.setString(2, progress.loopState().name());
            ps.setString(3, setToJson(progress.completedChangeNames()));
            ps.setInt(4, progress.totalIterations());
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save progress", e);
        }

        publishProgressSaved(progress.totalIterations(), progress.completedChangeNames().size());
    }

    @Override
    public Optional<LoopProgress> loadProgress() {
        String sql = "SELECT loop_state, completed_change_names, total_iterations FROM loop_progress WHERE id = 1";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (!rs.next()) {
                return Optional.empty();
            }
            return Optional.of(new LoopProgress(
                    LoopState.valueOf(rs.getString("loop_state")),
                    jsonToSet(rs.getString("completed_change_names")),
                    rs.getInt("total_iterations")
            ));
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load progress", e);
        }
    }

    @Override
    public void clear() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM loop_iterations");
            stmt.execute("DELETE FROM loop_progress");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to clear store", e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private LoopIteration resultSetToIteration(ResultSet rs) throws SQLException {
        long completedAt = rs.getLong("completed_at");
        boolean completedAtWasNull = rs.wasNull();
        return new LoopIteration(
                rs.getInt("iteration_number"),
                rs.getString("change_name"),
                rs.getString("milestone_file"),
                rs.getLong("started_at"),
                completedAtWasNull ? null : completedAt,
                IterationStatus.valueOf(rs.getString("status")),
                rs.getString("failure_reason")
        );
    }

    private Connection getConnection() throws SQLException {
        return DriverManager.getConnection(jdbcUrl, "root", "");
    }

    private void initTables() {
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_ITERATIONS_TABLE);
            stmt.execute(CREATE_PROGRESS_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize loop tables", e);
        }
    }

    private void publishProgressSaved(int iterationCount, int completedChangeCount) {
        try {
            eventBus.publish(new Event(EventType.LOOP_PROGRESS_SAVED,
                    System.currentTimeMillis(), "LoopIterationStore",
                    Map.of("iterationCount", iterationCount,
                            "completedChangeCount", completedChangeCount)));
        } catch (Exception e) {
            LOG.log(System.Logger.Level.WARNING, "Failed to publish progress event", e);
        }
    }

    // -------------------------------------------------------------------------
    // JSON serialization
    // -------------------------------------------------------------------------

    static String setToJson(Set<String> set) {
        if (set == null || set.isEmpty()) {
            return "[]";
        }
        JsonArray arr = new JsonArray();
        set.forEach(arr::add);
        return arr.encode();
    }

    static Set<String> jsonToSet(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank() || "[]".equals(jsonStr.trim())) {
            return Set.of();
        }
        JsonArray arr = new JsonArray(jsonStr);
        Set<String> result = new LinkedHashSet<>();
        for (Object item : arr) {
            if (item instanceof String s) {
                result.add(s);
            }
        }
        return Collections.unmodifiableSet(result);
    }
}

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
                token_usage            BIGINT NOT NULL DEFAULT 0,
                checkpoint_change_name VARCHAR(500),
                checkpoint_milestone_file VARCHAR(500),
                checkpoint_milestone_goal CLOB,
                checkpoint_planned_change_summary CLOB,
                checkpoint_completed_phases CLOB,
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
        String sql = """
                MERGE INTO loop_progress (
                    id, loop_state, completed_change_names, total_iterations, token_usage,
                    checkpoint_change_name, checkpoint_milestone_file, checkpoint_milestone_goal,
                    checkpoint_planned_change_summary, checkpoint_completed_phases, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            Optional<LoopPhaseCheckpoint> checkpoint = progress.activeCheckpoint();
            ps.setInt(1, 1);
            ps.setString(2, progress.loopState().name());
            ps.setString(3, setToJson(progress.completedChangeNames()));
            ps.setInt(4, progress.totalIterations());
            ps.setLong(5, progress.tokenUsage());
            if (checkpoint.isPresent()) {
                LoopPhaseCheckpoint active = checkpoint.get();
                ps.setString(6, active.changeName());
                ps.setString(7, active.milestoneFile());
                ps.setString(8, active.milestoneGoal());
                ps.setString(9, active.plannedChangeSummary());
                ps.setString(10, phasesToJson(active.completedPhases()));
            } else {
                ps.setNull(6, Types.VARCHAR);
                ps.setNull(7, Types.VARCHAR);
                ps.setNull(8, Types.CLOB);
                ps.setNull(9, Types.CLOB);
                ps.setNull(10, Types.CLOB);
            }
            ps.setLong(11, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save progress", e);
        }

        publishProgressSaved(progress.totalIterations(), progress.completedChangeNames().size());
    }

    @Override
    public Optional<LoopProgress> loadProgress() {
        String sql = """
                SELECT loop_state, completed_change_names, total_iterations, token_usage,
                       checkpoint_change_name, checkpoint_milestone_file, checkpoint_milestone_goal,
                       checkpoint_planned_change_summary, checkpoint_completed_phases
                FROM loop_progress WHERE id = 1
                """;
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            if (!rs.next()) {
                return Optional.empty();
            }
            long tokenUsage = rs.getLong("token_usage");
            boolean tokenUsageWasNull = rs.wasNull();
            return Optional.of(new LoopProgress(
                    LoopState.valueOf(rs.getString("loop_state")),
                    jsonToSet(rs.getString("completed_change_names")),
                    rs.getInt("total_iterations"),
                    tokenUsageWasNull ? 0 : tokenUsage,
                    resultSetToCheckpoint(rs)
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
            addColumnIfMissing(conn, "CHECKPOINT_CHANGE_NAME", "checkpoint_change_name VARCHAR(500)");
            addColumnIfMissing(conn, "CHECKPOINT_MILESTONE_FILE", "checkpoint_milestone_file VARCHAR(500)");
            addColumnIfMissing(conn, "CHECKPOINT_MILESTONE_GOAL", "checkpoint_milestone_goal CLOB");
            addColumnIfMissing(conn, "CHECKPOINT_PLANNED_CHANGE_SUMMARY",
                    "checkpoint_planned_change_summary CLOB");
            addColumnIfMissing(conn, "CHECKPOINT_COMPLETED_PHASES", "checkpoint_completed_phases CLOB");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize loop tables", e);
        }
    }

    private void addColumnIfMissing(Connection conn, String metadataName, String definition)
            throws SQLException {
        if (columnExists(conn, metadataName)) {
            return;
        }
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE loop_progress ADD " + definition);
        } catch (SQLException e) {
            if (!columnExists(conn, metadataName)) {
                throw e;
            }
        }
    }

    private boolean columnExists(Connection conn, String metadataName) throws SQLException {
        DatabaseMetaData metaData = conn.getMetaData();
        String lowerName = metadataName.toLowerCase(Locale.ROOT);
        for (String tableName : List.of("LOOP_PROGRESS", "loop_progress")) {
            for (String columnName : List.of(metadataName, lowerName)) {
                try (ResultSet columns = metaData.getColumns(null, null, tableName, columnName)) {
                    if (columns.next()) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private LoopPhaseCheckpoint resultSetToCheckpoint(ResultSet rs) throws SQLException {
        String changeName = rs.getString("checkpoint_change_name");
        String milestoneFile = rs.getString("checkpoint_milestone_file");
        if (changeName == null || changeName.isBlank()
                || milestoneFile == null || milestoneFile.isBlank()) {
            return null;
        }
        return new LoopPhaseCheckpoint(
                changeName,
                milestoneFile,
                rs.getString("checkpoint_milestone_goal"),
                rs.getString("checkpoint_planned_change_summary"),
                jsonToPhases(rs.getString("checkpoint_completed_phases"))
        );
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

    static String phasesToJson(List<PipelinePhase> phases) {
        if (phases == null || phases.isEmpty()) {
            return "[]";
        }
        JsonArray arr = new JsonArray();
        phases.forEach(phase -> arr.add(phase.name()));
        return arr.encode();
    }

    static List<PipelinePhase> jsonToPhases(String jsonStr) {
        if (jsonStr == null || jsonStr.isBlank() || "[]".equals(jsonStr.trim())) {
            return List.of();
        }
        JsonArray arr = new JsonArray(jsonStr);
        List<PipelinePhase> result = new ArrayList<>();
        for (Object item : arr) {
            if (item instanceof String s) {
                result.add(PipelinePhase.valueOf(s));
            }
        }
        return List.copyOf(result);
    }
}

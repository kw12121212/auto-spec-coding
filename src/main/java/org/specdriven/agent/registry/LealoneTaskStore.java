package org.specdriven.agent.registry;

import com.lealone.orm.json.JsonObject;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.sql.*;
import java.util.*;

/**
 * {@link TaskStore} backed by a Lealone SQL table.
 * Table is auto-created on first initialization.
 * A background VirtualThread cleans up DELETED tasks older than 7 days every hour.
 */
public class LealoneTaskStore implements TaskStore {

    private static final System.Logger LOG =
            System.getLogger(LealoneTaskStore.class.getName());

    private static final long DELETED_RETENTION_MS = 7L * 24 * 3600 * 1000;

    private static final String CREATE_TABLE = """
            CREATE TABLE IF NOT EXISTS tasks (
                id          VARCHAR(36)  PRIMARY KEY,
                title       VARCHAR(500) NOT NULL,
                description CLOB,
                status      VARCHAR(20)  NOT NULL,
                owner       VARCHAR(255),
                parent_task VARCHAR(36),
                metadata    CLOB,
                created_at  BIGINT       NOT NULL,
                updated_at  BIGINT       NOT NULL
            )
            """;

    private final String jdbcUrl;
    private final EventBus eventBus;

    /**
     * Creates and initializes the store.
     *
     * @param eventBus EventBus for publishing task lifecycle events
     * @param jdbcUrl  Lealone JDBC URL, e.g. {@code jdbc:lealone:embed:agent_db}
     */
    public LealoneTaskStore(EventBus eventBus, String jdbcUrl) {
        this.eventBus = eventBus;
        this.jdbcUrl = jdbcUrl;
        initTables();
        startCleanupThread();
    }

    // -------------------------------------------------------------------------
    // TaskStore implementation
    // -------------------------------------------------------------------------

    @Override
    public String save(Task task) {
        boolean isNew = task.id() == null;
        String id = isNew ? UUID.randomUUID().toString() : task.id();
        long now = System.currentTimeMillis();
        long createdAt = isNew ? now : task.createdAt();

        String upsert = "MERGE INTO tasks (id, title, description, status, owner, parent_task, metadata, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(upsert)) {
            ps.setString(1, id);
            ps.setString(2, task.title());
            ps.setString(3, task.description());
            ps.setString(4, task.status().name());
            ps.setString(5, task.owner());
            ps.setString(6, task.parentTaskId());
            ps.setString(7, mapToJson(task.metadata()));
            ps.setLong(8, createdAt);
            ps.setLong(9, now);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save task " + id, e);
        }

        if (isNew) {
            publishEvent(EventType.TASK_CREATED, id);
        }

        return id;
    }

    @Override
    public Optional<Task> load(String taskId) {
        String select = "SELECT id, title, description, status, owner, parent_task, metadata, created_at, updated_at FROM tasks WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(select)) {
            ps.setString(1, taskId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(resultSetToTask(rs));
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load task " + taskId, e);
        }
    }

    @Override
    public Task update(String taskId, TaskStatus newStatus) {
        Task existing = load(taskId).orElseThrow(() ->
                new NoSuchElementException("Task not found: " + taskId));
        TaskStatus.validateTransition(existing.status(), newStatus);

        long now = System.currentTimeMillis();
        String sql = "UPDATE tasks SET status = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, newStatus.name());
            ps.setLong(2, now);
            ps.setString(3, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update task status " + taskId, e);
        }

        if (newStatus == TaskStatus.COMPLETED) {
            publishEvent(EventType.TASK_COMPLETED, taskId);
        }

        return new Task(taskId, existing.title(), existing.description(), newStatus,
                existing.owner(), existing.parentTaskId(), existing.metadata(),
                existing.createdAt(), now);
    }

    @Override
    public Task update(String taskId, String title, String description) {
        Task existing = load(taskId).orElseThrow(() ->
                new NoSuchElementException("Task not found: " + taskId));

        long now = System.currentTimeMillis();
        String sql = "UPDATE tasks SET title = ?, description = ?, updated_at = ? WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, title);
            ps.setString(2, description);
            ps.setLong(3, now);
            ps.setString(4, taskId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to update task " + taskId, e);
        }

        return new Task(taskId, title, description, existing.status(),
                existing.owner(), existing.parentTaskId(), existing.metadata(),
                existing.createdAt(), now);
    }

    @Override
    public void delete(String taskId) {
        update(taskId, TaskStatus.DELETED);
    }

    @Override
    public List<Task> list() {
        return queryTasks("SELECT id, title, description, status, owner, parent_task, metadata, created_at, updated_at FROM tasks WHERE status <> ? ORDER BY created_at ASC",
                TaskStatus.DELETED);
    }

    @Override
    public List<Task> queryByStatus(TaskStatus status) {
        if (status == TaskStatus.DELETED) {
            return List.of();
        }
        return queryTasks("SELECT id, title, description, status, owner, parent_task, metadata, created_at, updated_at FROM tasks WHERE status = ? AND status <> ? ORDER BY created_at ASC",
                status, TaskStatus.DELETED);
    }

    @Override
    public List<Task> queryByOwner(String owner) {
        String sql = "SELECT id, title, description, status, owner, parent_task, metadata, created_at, updated_at FROM tasks WHERE owner = ? AND status <> ? ORDER BY created_at ASC";
        List<Task> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, owner);
            ps.setString(2, TaskStatus.DELETED.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(resultSetToTask(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query tasks by owner " + owner, e);
        }
        return Collections.unmodifiableList(result);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private List<Task> queryTasks(String sql, TaskStatus filterStatus) {
        List<Task> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, filterStatus.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(resultSetToTask(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query tasks", e);
        }
        return Collections.unmodifiableList(result);
    }

    private List<Task> queryTasks(String sql, TaskStatus matchStatus, TaskStatus excludeStatus) {
        List<Task> result = new ArrayList<>();
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, matchStatus.name());
            ps.setString(2, excludeStatus.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(resultSetToTask(rs));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to query tasks", e);
        }
        return Collections.unmodifiableList(result);
    }

    private Task resultSetToTask(ResultSet rs) throws SQLException {
        return new Task(
                rs.getString("id"),
                rs.getString("title"),
                rs.getString("description"),
                TaskStatus.valueOf(rs.getString("status")),
                rs.getString("owner"),
                rs.getString("parent_task"),
                jsonToMap(rs.getString("metadata")),
                rs.getLong("created_at"),
                rs.getLong("updated_at"));
    }

    private void publishEvent(EventType type, String taskId) {
        try {
            eventBus.publish(new Event(type, System.currentTimeMillis(), "TaskStore",
                    Map.of("taskId", taskId)));
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
            stmt.execute(CREATE_TABLE);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to initialize tasks table", e);
        }
    }

    private void startCleanupThread() {
        Thread.ofVirtual().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    Thread.sleep(3_600_000L);
                    cleanupDeleted();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    LOG.log(System.Logger.Level.WARNING, "Deleted-task cleanup failed", e);
                }
            }
        });
    }

    private void cleanupDeleted() throws SQLException {
        long cutoff = System.currentTimeMillis() - DELETED_RETENTION_MS;
        String sql = "DELETE FROM tasks WHERE status = ? AND updated_at < ?";
        try (Connection conn = getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, TaskStatus.DELETED.name());
            ps.setLong(2, cutoff);
            ps.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Metadata serialization
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

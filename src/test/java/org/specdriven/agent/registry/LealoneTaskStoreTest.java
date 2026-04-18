package org.specdriven.agent.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.testsupport.CapturingEventBus;
import org.specdriven.agent.testsupport.LealoneTestDb;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class LealoneTaskStoreTest {

    private LealoneTaskStore store;
    private CapturingEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new CapturingEventBus();
        store = new LealoneTaskStore(eventBus, LealoneTestDb.freshJdbcUrl());
    }

    // -------------------------------------------------------------------------
    // save → load round-trip
    // -------------------------------------------------------------------------

    @Test
    void saveAndLoad_returnsEquivalentTask() {
        Task task = new Task(null, "My Task", "A description", TaskStatus.PENDING,
                "agent-1", null, Map.of("priority", "high"), 0, 0);
        String id = store.save(task);

        Optional<Task> loaded = store.load(id);
        assertTrue(loaded.isPresent());
        Task t = loaded.get();
        assertEquals("My Task", t.title());
        assertEquals("A description", t.description());
        assertEquals(TaskStatus.PENDING, t.status());
        assertEquals("agent-1", t.owner());
        assertNull(t.parentTaskId());
        assertEquals("high", t.metadata().get("priority"));
        assertNotNull(t.id());
        assertFalse(t.id().isBlank());
        assertTrue(t.createdAt() > 0);
        assertTrue(t.updatedAt() > 0);
    }

    @Test
    void save_withNullId_generatesUuid() {
        Task task = new Task(null, "Task", null, TaskStatus.PENDING, null, null, null, 0, 0);
        String id = store.save(task);
        assertNotNull(id);
        assertFalse(id.isBlank());
        assertTrue(store.load(id).isPresent());
    }

    @Test
    void save_withExistingId_upserts() {
        Task task = new Task(null, "Original", null, TaskStatus.PENDING, null, null, null, 0, 0);
        String id = store.save(task);

        Task updated = new Task(id, "Updated", null, TaskStatus.PENDING, null, null, null, 0, 0);
        store.save(updated);

        Task loaded = store.load(id).orElseThrow();
        assertEquals("Updated", loaded.title());
    }

    // -------------------------------------------------------------------------
    // update status
    // -------------------------------------------------------------------------

    @Test
    void updateStatus_pending_toInProgress() {
        String id = store.save(new Task(null, "Task", null, TaskStatus.PENDING, null, null, null, 0, 0));

        Task updated = store.update(id, TaskStatus.IN_PROGRESS);

        assertEquals(TaskStatus.IN_PROGRESS, updated.status());
        assertEquals(id, updated.id());
        assertTrue(updated.updatedAt() >= updated.createdAt());
    }

    @Test
    void updateStatus_inProgress_toCompleted() {
        String id = store.save(new Task(null, "Task", null, TaskStatus.PENDING, null, null, null, 0, 0));
        store.update(id, TaskStatus.IN_PROGRESS);

        Task updated = store.update(id, TaskStatus.COMPLETED);

        assertEquals(TaskStatus.COMPLETED, updated.status());
    }

    // -------------------------------------------------------------------------
    // update title/description
    // -------------------------------------------------------------------------

    @Test
    void updateTitleDescription() {
        String id = store.save(new Task(null, "Old Title", "Old Desc", TaskStatus.PENDING, null, null, null, 0, 0));

        Task updated = store.update(id, "New Title", "New Desc");

        assertEquals("New Title", updated.title());
        assertEquals("New Desc", updated.description());
        assertEquals(TaskStatus.PENDING, updated.status());
    }

    // -------------------------------------------------------------------------
    // delete
    // -------------------------------------------------------------------------

    @Test
    void delete_transitionsToDeleted() {
        String id = store.save(new Task(null, "Task", null, TaskStatus.PENDING, null, null, null, 0, 0));

        store.delete(id);

        Task loaded = store.load(id).orElseThrow();
        assertEquals(TaskStatus.DELETED, loaded.status());
    }

    // -------------------------------------------------------------------------
    // Edge cases
    // -------------------------------------------------------------------------

    @Test
    void load_nonExistent_returnsEmpty() {
        assertTrue(store.load("nonexistent").isEmpty());
    }

    @Test
    void delete_nonExistent_throwsNoSuchElementException() {
        assertThrows(NoSuchElementException.class, () -> store.delete("nonexistent"));
    }

    @Test
    void updateStatus_nonExistent_throwsNoSuchElementException() {
        assertThrows(NoSuchElementException.class,
                () -> store.update("nonexistent", TaskStatus.IN_PROGRESS));
    }

    @Test
    void updateTitleDescription_nonExistent_throwsNoSuchElementException() {
        assertThrows(NoSuchElementException.class,
                () -> store.update("nonexistent", "title", "desc"));
    }

    @Test
    void list_emptyStore_returnsEmptyList() {
        assertTrue(store.list().isEmpty());
    }

    @Test
    void list_excludesDeleted() {
        store.save(new Task(null, "Active", null, TaskStatus.PENDING, null, null, null, 0, 0));
        String toDelete = store.save(new Task(null, "ToDelete", null, TaskStatus.PENDING, null, null, null, 0, 0));
        store.delete(toDelete);

        List<Task> tasks = store.list();
        assertEquals(1, tasks.size());
        assertEquals("Active", tasks.get(0).title());
    }

    @Test
    void queryByStatus_returnsMatchingTasks() {
        store.save(new Task(null, "T1", null, TaskStatus.PENDING, null, null, null, 0, 0));
        String id2 = store.save(new Task(null, "T2", null, TaskStatus.PENDING, null, null, null, 0, 0));
        store.update(id2, TaskStatus.IN_PROGRESS);

        List<Task> pending = store.queryByStatus(TaskStatus.PENDING);
        List<Task> inProgress = store.queryByStatus(TaskStatus.IN_PROGRESS);

        assertEquals(1, pending.size());
        assertEquals(1, inProgress.size());
    }

    @Test
    void queryByStatus_noMatch_returnsEmptyList() {
        assertTrue(store.queryByStatus(TaskStatus.COMPLETED).isEmpty());
    }

    @Test
    void queryByOwner_returnsMatchingTasks() {
        store.save(new Task(null, "T1", null, TaskStatus.PENDING, "alice", null, null, 0, 0));
        store.save(new Task(null, "T2", null, TaskStatus.PENDING, "bob", null, null, 0, 0));
        store.save(new Task(null, "T3", null, TaskStatus.PENDING, "alice", null, null, 0, 0));

        List<Task> aliceTasks = store.queryByOwner("alice");
        assertEquals(2, aliceTasks.size());
        assertTrue(aliceTasks.stream().allMatch(t -> "alice".equals(t.owner())));
    }

    @Test
    void queryByOwner_noMatch_returnsEmptyList() {
        assertTrue(store.queryByOwner("nobody").isEmpty());
    }

    // -------------------------------------------------------------------------
    // EventBus integration
    // -------------------------------------------------------------------------

    @Test
    void save_newTask_publishesTaskCreated() {
        store.save(new Task(null, "Task", null, TaskStatus.PENDING, null, null, null, 0, 0));

        assertEquals(1, eventBus.getEvents().size());
        Event event = eventBus.getEvents().get(0);
        assertEquals(EventType.TASK_CREATED, event.type());
        assertEquals("TaskStore", event.source());
        assertNotNull(event.metadata().get("taskId"));
    }

    @Test
    void updateStatus_toCompleted_publishesTaskCompleted() {
        String id = store.save(new Task(null, "Task", null, TaskStatus.PENDING, null, null, null, 0, 0));
        eventBus.clear();

        store.update(id, TaskStatus.IN_PROGRESS);
        store.update(id, TaskStatus.COMPLETED);

        assertEquals(1, eventBus.getEvents().size());
        Event event = eventBus.getEvents().get(0);
        assertEquals(EventType.TASK_COMPLETED, event.type());
        assertEquals(id, event.metadata().get("taskId"));
    }

    @Test
    void save_existingTask_doesNotPublishTaskCreated() {
        String id = store.save(new Task(null, "Task", null, TaskStatus.PENDING, null, null, null, 0, 0));
        eventBus.clear();

        store.save(new Task(id, "Updated", null, TaskStatus.PENDING, null, null, null, 0, 0));

        assertTrue(eventBus.getEvents().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Metadata serialization round-trip
    // -------------------------------------------------------------------------

    @Test
    void metadataSerialization_roundTrip() {
        Map<String, Object> meta = Map.of("string", "value", "number", 42, "bool", true);
        String id = store.save(new Task(null, "Task", null, TaskStatus.PENDING,
                null, null, meta, 0, 0));

        Task loaded = store.load(id).orElseThrow();
        assertEquals("value", loaded.metadata().get("string"));
        assertEquals(42, loaded.metadata().get("number"));
        assertEquals(true, loaded.metadata().get("bool"));
    }

    // -------------------------------------------------------------------------
    // Full CRUD lifecycle
    // -------------------------------------------------------------------------

    @Test
    void fullCrudLifecycle() {
        // Create
        String id = store.save(new Task(null, "Lifecycle Task", "Test lifecycle",
                TaskStatus.PENDING, "agent-1", null, Map.of("step", "init"), 0, 0));
        assertNotNull(id);

        // Load
        Task loaded = store.load(id).orElseThrow();
        assertEquals(TaskStatus.PENDING, loaded.status());
        assertEquals("Lifecycle Task", loaded.title());

        // Update status
        Task inProgress = store.update(id, TaskStatus.IN_PROGRESS);
        assertEquals(TaskStatus.IN_PROGRESS, inProgress.status());

        // Update title/description
        Task updated = store.update(id, "Updated Task", "Updated desc");
        assertEquals("Updated Task", updated.title());
        assertEquals("Updated desc", updated.description());
        assertEquals(TaskStatus.IN_PROGRESS, updated.status());

        // Complete
        Task completed = store.update(id, TaskStatus.COMPLETED);
        assertEquals(TaskStatus.COMPLETED, completed.status());

        // COMPLETED is terminal — cannot delete
        assertThrows(IllegalStateException.class, () -> store.delete(id));
    }

    @Test
    void fullCrud_deleteFromPending() {
        String id = store.save(new Task(null, "Delete Test", null,
                TaskStatus.PENDING, null, null, null, 0, 0));

        store.delete(id);
        Task deleted = store.load(id).orElseThrow();
        assertEquals(TaskStatus.DELETED, deleted.status());

        // Not in list
        assertTrue(store.list().stream().noneMatch(t -> t.id().equals(id)));
    }
}

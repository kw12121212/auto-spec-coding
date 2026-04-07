package org.specdriven.agent.registry;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class TaskTest {

    @Test
    void construction_withAllFields() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("key", "value");
        meta.put("num", 42);

        Task task = new Task("id-1", "Title", "Desc", TaskStatus.PENDING,
                "owner-1", "parent-1", meta, 1000L, 2000L);

        assertEquals("id-1", task.id());
        assertEquals("Title", task.title());
        assertEquals("Desc", task.description());
        assertEquals(TaskStatus.PENDING, task.status());
        assertEquals("owner-1", task.owner());
        assertEquals("parent-1", task.parentTaskId());
        assertEquals(1000L, task.createdAt());
        assertEquals(2000L, task.updatedAt());
        assertEquals(2, task.metadata().size());
        assertEquals("value", task.metadata().get("key"));
        assertEquals(42, task.metadata().get("num"));
    }

    @Test
    void nullMetadata_normalizedToEmptyMap() {
        Task task = new Task("id", "Title", null, TaskStatus.PENDING,
                null, null, null, 1000L, 2000L);
        assertNotNull(task.metadata());
        assertTrue(task.metadata().isEmpty());
    }

    @Test
    void defensiveCopy_metadataIsUnmodifiable() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("key", "value");

        Task task = new Task("id", "Title", null, TaskStatus.PENDING,
                null, null, meta, 1000L, 2000L);

        assertThrows(UnsupportedOperationException.class, () ->
                task.metadata().put("new", "value"));
    }

    @Test
    void defensiveCopy_originalMapMutationDoesNotAffectTask() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("key", "value");

        Task task = new Task("id", "Title", null, TaskStatus.PENDING,
                null, null, meta, 1000L, 2000L);

        meta.put("extra", "extra");
        assertEquals(1, task.metadata().size());
        assertFalse(task.metadata().containsKey("extra"));
    }
}

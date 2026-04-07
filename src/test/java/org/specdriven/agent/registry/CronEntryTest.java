package org.specdriven.agent.registry;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CronEntryTest {

    @Test
    void construction_withAllFields() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("key", "value");
        meta.put("num", 42);

        CronEntry entry = new CronEntry("id-1", "Test Job", "*/5 * * * *", 0,
                CronStatus.ACTIVE, "do something", meta, 1000L, 2000L, 3000L, 0L);

        assertEquals("id-1", entry.id());
        assertEquals("Test Job", entry.name());
        assertEquals("*/5 * * * *", entry.cronExpression());
        assertEquals(0, entry.delayMillis());
        assertEquals(CronStatus.ACTIVE, entry.status());
        assertEquals("do something", entry.prompt());
        assertEquals(3000L, entry.nextFireTime());
        assertEquals(0L, entry.lastFireTime());
        assertEquals(2, entry.metadata().size());
    }

    @Test
    void nullId_allowedBeforeSave() {
        CronEntry entry = new CronEntry(null, "Job", null, 5000,
                CronStatus.ACTIVE, null, null, 0, 0, 0, 0);
        assertNull(entry.id());
        assertEquals(5000, entry.delayMillis());
        assertNull(entry.cronExpression());
    }

    @Test
    void nullMetadata_normalizedToEmptyMap() {
        CronEntry entry = new CronEntry("id", "Job", "* * * * *", 0,
                CronStatus.ACTIVE, null, null, 0, 0, 0, 0);
        assertNotNull(entry.metadata());
        assertTrue(entry.metadata().isEmpty());
    }

    @Test
    void defensiveCopy_metadataIsUnmodifiable() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("key", "value");

        CronEntry entry = new CronEntry("id", "Job", "* * * * *", 0,
                CronStatus.ACTIVE, null, meta, 0, 0, 0, 0);

        assertThrows(UnsupportedOperationException.class, () ->
                entry.metadata().put("new", "value"));
    }

    @Test
    void defensiveCopy_originalMapMutationDoesNotAffectEntry() {
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("key", "value");

        CronEntry entry = new CronEntry("id", "Job", "* * * * *", 0,
                CronStatus.ACTIVE, null, meta, 0, 0, 0, 0);

        meta.put("extra", "extra");
        assertEquals(1, entry.metadata().size());
        assertFalse(entry.metadata().containsKey("extra"));
    }
}

package org.specdriven.agent.event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class LealoneAuditLogStoreTest {

    private LealoneAuditLogStore store;
    private SimpleEventBus eventBus;
    private String jdbcUrl;

    @BeforeEach
    void setUp() {
        String dbName = "test_audit_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
        eventBus = new SimpleEventBus();
        store = new LealoneAuditLogStore(eventBus, jdbcUrl);
    }

    // -------------------------------------------------------------------------
    // save and retrieve a single event
    // -------------------------------------------------------------------------

    @Test
    void saveAndRetrieve_singleEvent() {
        Event event = new Event(EventType.TOOL_EXECUTED, 1000L, "bash", Map.of("exitCode", 0));
        long id = store.save(event);

        assertTrue(id > 0);
        assertEquals(1, store.count());

        List<AuditEntry> entries = store.query(EventType.TOOL_EXECUTED, 0L, 2000L);
        assertEquals(1, entries.size());

        AuditEntry entry = entries.get(0);
        assertEquals(id, entry.id());
        assertEquals(EventType.TOOL_EXECUTED, entry.event().type());
        assertEquals("bash", entry.event().source());
        assertEquals(1000L, entry.event().timestamp());
        assertEquals(0, entry.event().metadata().get("exitCode"));
    }

    // -------------------------------------------------------------------------
    // query by EventType and time range
    // -------------------------------------------------------------------------

    @Test
    void query_byTypeAndTimeRange_returnsMatchingEntries() {
        store.save(new Event(EventType.TOOL_EXECUTED, 1000L, "bash", Map.of()));
        store.save(new Event(EventType.AGENT_STATE_CHANGED, 2000L, "agent-1", Map.of()));
        store.save(new Event(EventType.TOOL_EXECUTED, 3000L, "grep", Map.of()));

        List<AuditEntry> results = store.query(EventType.TOOL_EXECUTED, 500L, 3500L);

        assertEquals(2, results.size());
        assertEquals("bash", results.get(0).event().source());
        assertEquals("grep", results.get(1).event().source());
    }

    @Test
    void query_timeRangeIsInclusive() {
        store.save(new Event(EventType.ERROR, 1000L, "src", Map.of()));
        store.save(new Event(EventType.ERROR, 2000L, "src", Map.of()));
        store.save(new Event(EventType.ERROR, 3000L, "src", Map.of()));

        List<AuditEntry> results = store.query(EventType.ERROR, 1000L, 3000L);
        assertEquals(3, results.size());
    }

    // -------------------------------------------------------------------------
    // queryBySource
    // -------------------------------------------------------------------------

    @Test
    void queryBySource_returnsMatchingEntries() {
        store.save(new Event(EventType.TOOL_EXECUTED, 1000L, "bash", Map.of()));
        store.save(new Event(EventType.TOOL_EXECUTED, 2000L, "grep", Map.of()));
        store.save(new Event(EventType.AGENT_STATE_CHANGED, 3000L, "bash", Map.of()));

        List<AuditEntry> results = store.queryBySource("bash", 0L, 5000L);

        assertEquals(2, results.size());
        assertEquals(EventType.TOOL_EXECUTED, results.get(0).event().type());
        assertEquals(EventType.AGENT_STATE_CHANGED, results.get(1).event().type());
    }

    // -------------------------------------------------------------------------
    // query with no matches returns empty list
    // -------------------------------------------------------------------------

    @Test
    void query_noMatches_returnsEmptyList() {
        store.save(new Event(EventType.TOOL_EXECUTED, 1000L, "bash", Map.of()));

        List<AuditEntry> results = store.query(EventType.CRON_TRIGGERED, 0L, 5000L);
        assertTrue(results.isEmpty());

        results = store.queryBySource("nonexistent", 0L, 5000L);
        assertTrue(results.isEmpty());
    }

    // -------------------------------------------------------------------------
    // deleteOlderThan
    // -------------------------------------------------------------------------

    @Test
    void deleteOlderThan_removesEntriesAndReturnsCount() {
        store.save(new Event(EventType.TOOL_EXECUTED, 1000L, "bash", Map.of()));
        store.save(new Event(EventType.ERROR, 2000L, "src", Map.of()));
        store.save(new Event(EventType.TOOL_EXECUTED, 5000L, "grep", Map.of()));

        int deleted = store.deleteOlderThan(3000L);

        assertEquals(2, deleted);
        assertEquals(1, store.count());

        List<AuditEntry> remaining = store.query(EventType.TOOL_EXECUTED, 0L, 10000L);
        assertEquals(1, remaining.size());
        assertEquals(5000L, remaining.get(0).event().timestamp());
    }

    @Test
    void deleteOlderThan_noMatches_returnsZero() {
        store.save(new Event(EventType.TOOL_EXECUTED, 5000L, "bash", Map.of()));

        int deleted = store.deleteOlderThan(1000L);
        assertEquals(0, deleted);
        assertEquals(1, store.count());
    }

    // -------------------------------------------------------------------------
    // count
    // -------------------------------------------------------------------------

    @Test
    void count_returnsTotalEntryCount() {
        assertEquals(0, store.count());

        store.save(new Event(EventType.TOOL_EXECUTED, 1000L, "bash", Map.of()));
        assertEquals(1, store.count());

        store.save(new Event(EventType.ERROR, 2000L, "src", Map.of()));
        assertEquals(2, store.count());
    }

    @Test
    void count_emptyStore_returnsZero() {
        assertEquals(0, store.count());
    }

    // -------------------------------------------------------------------------
    // EventBus subscription
    // -------------------------------------------------------------------------

    @Test
    void eventBusSubscription_autoPersistsEvents() {
        eventBus.publish(new Event(EventType.TASK_CREATED, 1000L, "orchestrator", Map.of("taskId", "t1")));
        eventBus.publish(new Event(EventType.TASK_COMPLETED, 2000L, "orchestrator", Map.of("taskId", "t1")));

        assertEquals(2, store.count());

        List<AuditEntry> entries = store.query(EventType.TASK_CREATED, 0L, 5000L);
        assertEquals(1, entries.size());
        assertEquals("orchestrator", entries.get(0).event().source());
    }

    // -------------------------------------------------------------------------
    // AuditEntry record
    // -------------------------------------------------------------------------

    @Test
    void auditEntry_holdsIdAndEvent() {
        Event event = new Event(EventType.CRON_TRIGGERED, 42L, "scheduler", Map.of());
        AuditEntry entry = new AuditEntry(99L, event);

        assertEquals(99L, entry.id());
        assertSame(event, entry.event());
        assertEquals(EventType.CRON_TRIGGERED, entry.event().type());
        assertEquals(42L, entry.event().timestamp());
        assertEquals("scheduler", entry.event().source());
    }
}

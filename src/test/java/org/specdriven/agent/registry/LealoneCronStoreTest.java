package org.specdriven.agent.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.testsupport.CapturingEventBus;
import org.specdriven.agent.testsupport.LealoneTestDb;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

@Isolated
class LealoneCronStoreTest {

    private LealoneCronStore store;
    private CapturingEventBus eventBus;
    private AtomicInteger fireCount;
    private CountDownLatch fireLatch;
    private CountDownLatch eventLatch;

    @BeforeEach
    void setUp() {
        eventBus = new CapturingEventBus();
        fireCount = new AtomicInteger(0);
        fireLatch = new CountDownLatch(1);
        eventLatch = new CountDownLatch(1);
        EventBus latchedBus = new EventBus() {
            @Override public void publish(Event e) { eventBus.publish(e); eventLatch.countDown(); }
            @Override public void subscribe(EventType t, Consumer<Event> l) {}
            @Override public void unsubscribe(EventType t, Consumer<Event> l) {}
        };
        store = new LealoneCronStore(latchedBus, LealoneTestDb.freshJdbcUrl(), () -> {
            fireCount.incrementAndGet();
            fireLatch.countDown();
        });
    }

    // -------------------------------------------------------------------------
    // create → load round-trip
    // -------------------------------------------------------------------------

    @Test
    void createAndLoad_recurring() {
        CronEntry entry = new CronEntry(null, "Every minute", "* * * * *", 0,
                CronStatus.ACTIVE, "do work", Map.of("env", "prod"), 0, 0, 0, 0);
        String id = store.create(entry);

        Optional<CronEntry> loaded = store.load(id);
        assertTrue(loaded.isPresent());
        CronEntry e = loaded.get();
        assertEquals("Every minute", e.name());
        assertEquals("* * * * *", e.cronExpression());
        assertEquals(0, e.delayMillis());
        assertEquals(CronStatus.ACTIVE, e.status());
        assertEquals("do work", e.prompt());
        assertEquals("prod", e.metadata().get("env"));
        assertNotNull(e.id());
        assertFalse(e.id().isBlank());
        assertTrue(e.createdAt() > 0);
        assertTrue(e.updatedAt() > 0);
        assertTrue(e.nextFireTime() > 0);
    }

    @Test
    void create_withNullId_generatesUuid() {
        CronEntry entry = new CronEntry(null, "Job", "* * * * *", 0,
                CronStatus.ACTIVE, null, null, 0, 0, 0, 0);
        String id = store.create(entry);
        assertNotNull(id);
        assertFalse(id.isBlank());
        assertTrue(store.load(id).isPresent());
    }

    // -------------------------------------------------------------------------
    // One-shot entries
    // -------------------------------------------------------------------------

    @Test
    void createAndLoad_oneShot() {
        CronEntry entry = new CronEntry(null, "Delay job", null, 5000,
                CronStatus.ACTIVE, "delayed task", null, 0, 0, 0, 0);
        String id = store.create(entry);

        CronEntry loaded = store.load(id).orElseThrow();
        assertNull(loaded.cronExpression());
        assertEquals(5000, loaded.delayMillis());
        assertEquals(CronStatus.ACTIVE, loaded.status());
        assertTrue(loaded.nextFireTime() > System.currentTimeMillis());
    }

    @Test
    void oneShot_firesAfterDelay() throws InterruptedException {
        CronEntry entry = new CronEntry(null, "Quick one-shot", null, 2000,
                CronStatus.ACTIVE, "fired", null, 0, 0, 0, 0);
        store.create(entry);

        assertTrue(fireLatch.await(5, TimeUnit.SECONDS));
        assertTrue(eventLatch.await(5, TimeUnit.SECONDS));

        assertEquals(1, fireCount.get());
        assertEquals(1, eventBus.getEvents().size());
        Event event = eventBus.getEvents().get(0);
        assertEquals(EventType.CRON_TRIGGERED, event.type());
        assertEquals("CronStore", event.source());
    }

    // -------------------------------------------------------------------------
    // cancel
    // -------------------------------------------------------------------------

    @Test
    void cancel_transitionsToCancelled() {
        String id = store.create(new CronEntry(null, "Job", "* * * * *", 0,
                CronStatus.ACTIVE, null, null, 0, 0, 0, 0));

        store.cancel(id);

        CronEntry loaded = store.load(id).orElseThrow();
        assertEquals(CronStatus.CANCELLED, loaded.status());
    }

    @Test
    void cancel_nonExistent_throwsNoSuchElementException() {
        assertThrows(NoSuchElementException.class, () -> store.cancel("nonexistent"));
    }

    @Test
    void cancel_alreadyCancelled_throwsIllegalStateException() {
        String id = store.create(new CronEntry(null, "Job", "* * * * *", 0,
                CronStatus.ACTIVE, null, null, 0, 0, 0, 0));
        store.cancel(id);

        assertThrows(IllegalStateException.class, () -> store.cancel(id));
    }

    // -------------------------------------------------------------------------
    // list
    // -------------------------------------------------------------------------

    @Test
    void list_emptyStore_returnsEmptyList() {
        assertTrue(store.list().isEmpty());
    }

    @Test
    void list_excludesCancelled() {
        store.create(new CronEntry(null, "Active", "* * * * *", 0,
                CronStatus.ACTIVE, null, null, 0, 0, 0, 0));
        String toCancel = store.create(new CronEntry(null, "To cancel", "* * * * *", 0,
                CronStatus.ACTIVE, null, null, 0, 0, 0, 0));
        store.cancel(toCancel);

        List<CronEntry> entries = store.list();
        assertEquals(1, entries.size());
        assertEquals("Active", entries.get(0).name());
    }

    // -------------------------------------------------------------------------
    // queryByStatus
    // -------------------------------------------------------------------------

    @Test
    void queryByStatus_noMatches_returnsEmptyList() {
        assertTrue(store.queryByStatus(CronStatus.FIRED).isEmpty());
    }

    @Test
    void queryByStatus_returnsMatchingEntries() {
        String id = store.create(new CronEntry(null, "One-shot", null, 999999,
                CronStatus.ACTIVE, null, null, 0, 0, 0, 0));
        List<CronEntry> active = store.queryByStatus(CronStatus.ACTIVE);
        assertEquals(1, active.size());
        assertEquals(id, active.get(0).id());
    }

    // -------------------------------------------------------------------------
    // load edge cases
    // -------------------------------------------------------------------------

    @Test
    void load_nonExistent_returnsEmpty() {
        assertTrue(store.load("nonexistent").isEmpty());
    }

    // -------------------------------------------------------------------------
    // EventBus integration
    // -------------------------------------------------------------------------

    @Test
    void oneShot_publishesCronTriggered() throws InterruptedException {
        store.create(new CronEntry(null, "Quick job", null, 1000,
                CronStatus.ACTIVE, "work", Map.of("tag", "test"), 0, 0, 0, 0));

        assertTrue(fireLatch.await(5, TimeUnit.SECONDS));
        assertTrue(eventLatch.await(5, TimeUnit.SECONDS));

        assertFalse(eventBus.getEvents().isEmpty());
        Event event = eventBus.getEvents().get(0);
        assertEquals(EventType.CRON_TRIGGERED, event.type());
        assertEquals("CronStore", event.source());
        assertNotNull(event.metadata().get("entryId"));
        assertEquals("Quick job", event.metadata().get("entryName"));
    }

    // -------------------------------------------------------------------------
    // Metadata serialization round-trip
    // -------------------------------------------------------------------------

    @Test
    void metadataSerialization_roundTrip() {
        Map<String, Object> meta = Map.of("string", "value", "number", 42, "bool", true);
        String id = store.create(new CronEntry(null, "Job", "* * * * *", 0,
                CronStatus.ACTIVE, null, meta, 0, 0, 0, 0));

        CronEntry loaded = store.load(id).orElseThrow();
        assertEquals("value", loaded.metadata().get("string"));
        assertEquals(42, loaded.metadata().get("number"));
        assertEquals(true, loaded.metadata().get("bool"));
    }

    // -------------------------------------------------------------------------
    // Full CRUD lifecycle
    // -------------------------------------------------------------------------

    @Test
    void fullCrudLifecycle() {
        // Create recurring
        String id = store.create(new CronEntry(null, "Daily job", "0 9 * * *", 0,
                CronStatus.ACTIVE, "daily work", Map.of("env", "prod"), 0, 0, 0, 0));
        assertNotNull(id);

        // Load
        CronEntry loaded = store.load(id).orElseThrow();
        assertEquals(CronStatus.ACTIVE, loaded.status());
        assertEquals("0 9 * * *", loaded.cronExpression());
        assertTrue(loaded.nextFireTime() > 0);

        // List includes it
        List<CronEntry> active = store.list();
        assertEquals(1, active.size());

        // Cancel
        store.cancel(id);
        CronEntry cancelled = store.load(id).orElseThrow();
        assertEquals(CronStatus.CANCELLED, cancelled.status());

        // List excludes it
        assertTrue(store.list().isEmpty());

        // Terminal state
        assertThrows(IllegalStateException.class, () -> store.cancel(id));
    }

}

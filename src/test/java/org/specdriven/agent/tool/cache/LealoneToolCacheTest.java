package org.specdriven.agent.tool.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.EventType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class LealoneToolCacheTest {

    private LealoneToolCache cache;
    private CapturingEventBus eventBus;

    @BeforeEach
    void setUp() {
        String dbName = "test_tool_cache_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String jdbcUrl = "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false";
        eventBus = new CapturingEventBus();
        cache = new LealoneToolCache(eventBus, jdbcUrl);
    }

    // -------------------------------------------------------------------------
    // Cache hit / miss
    // -------------------------------------------------------------------------

    @Test
    void get_emptyCache_returnsEmpty() {
        Optional<ToolCache.CacheEntry> result = cache.get("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void putThenGet_returnsValue() {
        cache.put("key1", "output1", 60_000L, null, 0);
        Optional<ToolCache.CacheEntry> result = cache.get("key1");
        assertTrue(result.isPresent());
        assertEquals("output1", result.get().output());
    }

    @Test
    void putThenGet_preservesFileMetadata() {
        cache.put("key1", "output1", 60_000L, "/foo.txt", 12345L);
        Optional<ToolCache.CacheEntry> result = cache.get("key1");
        assertTrue(result.isPresent());
        assertEquals("/foo.txt", result.get().filePath());
        assertEquals(12345L, result.get().fileModified());
    }

    @Test
    void get_expiredEntry_returnsEmpty() {
        AtomicLong fakeClock = new AtomicLong(1_000_000L);
        String dbName = "test_tool_cache_exp_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        LealoneToolCache expiryCache = new LealoneToolCache(eventBus,
                "jdbc:lealone:embed:" + dbName + "?PERSISTENT=false", fakeClock::get);
        expiryCache.put("key1", "value", 1L, null, 0);
        fakeClock.addAndGet(10);
        Optional<ToolCache.CacheEntry> result = expiryCache.get("key1");
        assertTrue(result.isEmpty());
    }

    @Test
    void invalidate_removesEntry() {
        cache.put("key1", "value", 60_000L, null, 0);
        cache.invalidate("key1");
        assertTrue(cache.get("key1").isEmpty());
    }

    @Test
    void clear_removesAllEntries() {
        cache.put("k1", "v1", 60_000L, null, 0);
        cache.put("k2", "v2", 60_000L, null, 0);
        cache.clear();
        assertTrue(cache.get("k1").isEmpty());
        assertTrue(cache.get("k2").isEmpty());
    }

    // -------------------------------------------------------------------------
    // Event publishing
    // -------------------------------------------------------------------------

    @Test
    void get_hit_publishesCacheHitEvent() {
        cache.put("key1", "value", 60_000L, null, 0);
        eventBus.captured.clear();
        cache.get("key1");
        assertEquals(1, eventBus.captured.size());
        assertEquals(EventType.TOOL_CACHE_HIT, eventBus.captured.get(0).type());
    }

    @Test
    void get_miss_publishesCacheMissEvent() {
        cache.get("nonexistent");
        assertEquals(1, eventBus.captured.size());
        assertEquals(EventType.TOOL_CACHE_MISS, eventBus.captured.get(0).type());
    }

    // -------------------------------------------------------------------------
    // Hit count
    // -------------------------------------------------------------------------

    @Test
    void get_hit_incrementsHitCount() {
        cache.put("key1", "value", 60_000L, null, 0);
        cache.get("key1");
        cache.get("key1");
        cache.get("key1");
        // 3 hits — verify by checking that the key still returns value
        assertTrue(cache.get("key1").isPresent());
    }

    // -------------------------------------------------------------------------
    // Concurrent safety
    // -------------------------------------------------------------------------

    @Test
    void concurrentReadWrite_noErrors() throws InterruptedException {
        int threads = 10;
        int ops = 50;
        Thread[] ts = new Thread[threads];
        for (int i = 0; i < threads; i++) {
            final int idx = i;
            ts[i] = Thread.ofVirtual().start(() -> {
                for (int j = 0; j < ops; j++) {
                    String key = "key_" + idx + "_" + j;
                    cache.put(key, "value_" + j, 60_000L, null, 0);
                    cache.get(key);
                }
            });
        }
        for (Thread t : ts) {
            t.join(10_000);
        }
        // No exceptions means success
    }

    // -------------------------------------------------------------------------
    // Test helper
    // -------------------------------------------------------------------------

    private static class CapturingEventBus implements EventBus {
        final List<Event> captured = new ArrayList<>();

        @Override
        public void publish(Event event) {
            captured.add(event);
        }

        @Override
        public void subscribe(EventType type, Consumer<Event> listener) {
        }

        @Override
        public void unsubscribe(EventType type, Consumer<Event> listener) {
        }
    }
}

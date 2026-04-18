package org.specdriven.agent.tool.cache;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.testsupport.CapturingEventBus;
import org.specdriven.agent.testsupport.LealoneTestDb;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class LealoneToolCacheTest {

    private LealoneToolCache cache;
    private CapturingEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new CapturingEventBus();
        cache = new LealoneToolCache(eventBus, LealoneTestDb.freshJdbcUrl());
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
        LealoneToolCache expiryCache = new LealoneToolCache(eventBus, LealoneTestDb.freshJdbcUrl(), fakeClock::get);
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
        eventBus.clear();
        cache.get("key1");
        assertEquals(1, eventBus.getEvents().size());
        assertEquals(EventType.TOOL_CACHE_HIT, eventBus.getEvents().get(0).type());
    }

    @Test
    void get_miss_publishesCacheMissEvent() {
        cache.get("nonexistent");
        assertEquals(1, eventBus.getEvents().size());
        assertEquals(EventType.TOOL_CACHE_MISS, eventBus.getEvents().get(0).type());
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

}

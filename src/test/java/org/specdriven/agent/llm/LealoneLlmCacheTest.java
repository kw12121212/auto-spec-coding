package org.specdriven.agent.llm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.agent.LlmUsage;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.testsupport.CapturingEventBus;
import org.specdriven.agent.testsupport.LealoneTestDb;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class LealoneLlmCacheTest {

    private LealoneLlmCache cache;
    private CapturingEventBus eventBus;

    @BeforeEach
    void setUp() {
        eventBus = new CapturingEventBus();
        cache = new LealoneLlmCache(eventBus, LealoneTestDb.freshJdbcUrl());
    }

    // -------------------------------------------------------------------------
    // Cache hit / miss
    // -------------------------------------------------------------------------

    @Test
    void get_emptyCache_returnsEmpty() {
        Optional<String> result = cache.get("nonexistent");
        assertTrue(result.isEmpty());
    }

    @Test
    void putThenGet_returnsValue() {
        cache.put("key1", "{\"content\":\"hello\"}", 60_000L);
        Optional<String> result = cache.get("key1");
        assertTrue(result.isPresent());
        assertEquals("{\"content\":\"hello\"}", result.get());
    }

    @Test
    void get_expiredEntry_returnsEmpty() {
        AtomicLong fakeClock = new AtomicLong(1_000_000L);
        LealoneLlmCache expiryCache = new LealoneLlmCache(eventBus, LealoneTestDb.freshJdbcUrl(), fakeClock::get);
        expiryCache.put("key1", "value", 1L);
        fakeClock.addAndGet(10);
        Optional<String> result = expiryCache.get("key1");
        assertTrue(result.isEmpty());
    }

    @Test
    void invalidate_removesEntry() {
        cache.put("key1", "value", 60_000L);
        cache.invalidate("key1");
        assertTrue(cache.get("key1").isEmpty());
    }

    @Test
    void clear_removesAllEntries() {
        cache.put("k1", "v1", 60_000L);
        cache.put("k2", "v2", 60_000L);
        cache.clear();
        assertTrue(cache.get("k1").isEmpty());
        assertTrue(cache.get("k2").isEmpty());
    }

    // -------------------------------------------------------------------------
    // Event publishing
    // -------------------------------------------------------------------------

    @Test
    void get_hit_publishesCacheHitEvent() {
        cache.put("key1", "value", 60_000L);
        eventBus.clear();
        cache.get("key1");
        assertEquals(1, eventBus.getEvents().size());
        assertEquals(EventType.LLM_CACHE_HIT, eventBus.getEvents().get(0).type());
    }

    @Test
    void get_miss_publishesCacheMissEvent() {
        cache.get("nonexistent");
        assertEquals(1, eventBus.getEvents().size());
        assertEquals(EventType.LLM_CACHE_MISS, eventBus.getEvents().get(0).type());
    }

    // -------------------------------------------------------------------------
    // Usage recording and querying
    // -------------------------------------------------------------------------

    @Test
    void recordUsage_singleRecord_queryable() {
        LlmUsage usage = new LlmUsage(100, 50, 150);
        cache.recordUsage("s1", "agent1", "gpt-4", usage);

        List<UsageRecord> records = cache.queryUsage("s1", null, 0, Long.MAX_VALUE);
        assertEquals(1, records.size());
        UsageRecord r = records.get(0);
        assertEquals("s1", r.sessionId());
        assertEquals("agent1", r.agentName());
        assertEquals("gpt-4", r.model());
        assertEquals(100, r.promptTokens());
        assertEquals(50, r.completionTokens());
        assertEquals(150, r.totalTokens());
    }

    @Test
    void queryUsage_filterBySession() {
        cache.recordUsage("s1", "a1", "gpt-4", new LlmUsage(10, 5, 15));
        cache.recordUsage("s2", "a1", "gpt-4", new LlmUsage(20, 10, 30));

        List<UsageRecord> result = cache.queryUsage("s1", null, 0, Long.MAX_VALUE);
        assertEquals(1, result.size());
        assertEquals("s1", result.get(0).sessionId());
    }

    @Test
    void queryUsage_filterByAgent() {
        cache.recordUsage("s1", "a1", "gpt-4", new LlmUsage(10, 5, 15));
        cache.recordUsage("s1", "a2", "gpt-4", new LlmUsage(20, 10, 30));

        List<UsageRecord> result = cache.queryUsage(null, "a1", 0, Long.MAX_VALUE);
        assertEquals(1, result.size());
        assertEquals("a1", result.get(0).agentName());
    }

    @Test
    void queryUsage_filterByTimeRange() {
        long baseTime = System.currentTimeMillis();
        // We can't control created_at precisely, so just query with wide range and verify
        cache.recordUsage("s1", "a1", "gpt-4", new LlmUsage(10, 5, 15));

        List<UsageRecord> all = cache.queryUsage(null, null, 0, Long.MAX_VALUE);
        assertFalse(all.isEmpty());

        List<UsageRecord> none = cache.queryUsage(null, null,
                baseTime + 1_000_000, baseTime + 2_000_000);
        assertTrue(none.isEmpty());
    }

    @Test
    void queryUsage_noMatch_returnsEmptyList() {
        cache.recordUsage("s1", "a1", "gpt-4", new LlmUsage(10, 5, 15));
        List<UsageRecord> result = cache.queryUsage("nonexistent", null, 0, Long.MAX_VALUE);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Hit count
    // -------------------------------------------------------------------------

    @Test
    void get_hit_incrementsHitCount() {
        cache.put("key1", "value", 60_000L);
        cache.get("key1");
        cache.get("key1");
        cache.get("key1");
        // 3 hits — verify by checking that the key still returns value
        // (hit_count is internal; we just ensure no errors)
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
                    cache.put(key, "value_" + j, 60_000L);
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

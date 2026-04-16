package org.specdriven.sdk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.Event;
import org.specdriven.agent.event.EventType;
import org.specdriven.agent.event.SimpleEventBus;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class PlatformMetricsTest {

    private SimpleEventBus eventBus;
    private LealonePlatform platform;

    @BeforeEach
    void setUp() {
        eventBus = new SimpleEventBus();
        Path cachePath = Path.of(System.getProperty("java.io.tmpdir"), "pm-test-cache");
        platform = new LealonePlatform(
                new LealonePlatform.DatabaseCapability("jdbc:lealone:embed:pm_test_db"),
                new LealonePlatform.LlmCapability(
                        new org.specdriven.agent.agent.DefaultLlmProviderRegistry(null, eventBus, null),
                        Optional.empty()),
                new LealonePlatform.CompilerCapability(
                        new org.specdriven.skill.compiler.LealoneSkillSourceCompiler(),
                        new org.specdriven.skill.compiler.LealoneClassCacheManager(cachePath),
                        new org.specdriven.skill.hotload.LealoneSkillHotLoader(
                                new org.specdriven.skill.compiler.LealoneSkillSourceCompiler(),
                                new org.specdriven.skill.compiler.LealoneClassCacheManager(cachePath),
                                false),
                        cachePath),
                new LealonePlatform.InteractiveCapability(
                        sessionId -> new org.specdriven.agent.interactive.LealoneAgentAdapter(
                                "jdbc:lealone:embed:pm_test_db")),
                eventBus);
        platform.start();
    }

    @AfterEach
    void tearDown() {
        platform.close();
    }

    @Test
    void metrics_initialCountersAreZero() {
        PlatformMetrics metrics = platform.metrics();

        assertEquals(0L, metrics.promptTokens());
        assertEquals(0L, metrics.completionTokens());
        assertEquals(0L, metrics.compilationOps());
        assertEquals(0L, metrics.llmCacheHits());
        assertEquals(0L, metrics.llmCacheMisses());
        assertEquals(0L, metrics.toolCacheHits());
        assertEquals(0L, metrics.toolCacheMisses());
        assertEquals(0L, metrics.interactionCount());
        assertTrue(metrics.snapshotAt() > 0);
    }

    @Test
    void metrics_compilationOpsIncrementsOnSkillHotLoadEvent() {
        eventBus.publish(new Event(EventType.SKILL_HOT_LOAD_OPERATION,
                System.currentTimeMillis(), "test", Map.of("operation", "load", "skillName", "foo")));

        assertEquals(1L, platform.metrics().compilationOps());
    }

    @Test
    void metrics_interactionCountIncrementsOnInteractiveCommandEvent() {
        eventBus.publish(new Event(EventType.INTERACTIVE_COMMAND_HANDLED,
                System.currentTimeMillis(), "test", Map.of("sessionId", "s1", "command", "show")));

        assertEquals(1L, platform.metrics().interactionCount());
    }

    @Test
    void metrics_llmCacheHitsAndMissesAccumulate() {
        eventBus.publish(new Event(EventType.LLM_CACHE_HIT, System.currentTimeMillis(), "cache", Map.of()));
        eventBus.publish(new Event(EventType.LLM_CACHE_HIT, System.currentTimeMillis(), "cache", Map.of()));
        eventBus.publish(new Event(EventType.LLM_CACHE_MISS, System.currentTimeMillis(), "cache", Map.of()));

        PlatformMetrics metrics = platform.metrics();
        assertEquals(2L, metrics.llmCacheHits());
        assertEquals(1L, metrics.llmCacheMisses());
    }

    @Test
    void metrics_toolCacheHitsAndMissesAccumulate() {
        eventBus.publish(new Event(EventType.TOOL_CACHE_HIT, System.currentTimeMillis(), "cache", Map.of()));
        eventBus.publish(new Event(EventType.TOOL_CACHE_MISS, System.currentTimeMillis(), "cache", Map.of()));
        eventBus.publish(new Event(EventType.TOOL_CACHE_MISS, System.currentTimeMillis(), "cache", Map.of()));

        PlatformMetrics metrics = platform.metrics();
        assertEquals(1L, metrics.toolCacheHits());
        assertEquals(2L, metrics.toolCacheMisses());
    }

    @Test
    void metrics_publishesPlatformMetricsSnapshotEvent() {
        List<Event> captured = new ArrayList<>();
        eventBus.subscribe(EventType.PLATFORM_METRICS_SNAPSHOT, captured::add);

        eventBus.publish(new Event(EventType.SKILL_HOT_LOAD_OPERATION,
                System.currentTimeMillis(), "test", Map.of("operation", "load", "skillName", "foo")));

        platform.metrics();

        assertEquals(1, captured.size());
        Event e = captured.get(0);
        assertEquals(EventType.PLATFORM_METRICS_SNAPSHOT, e.type());
        assertTrue(e.metadata().containsKey("compilationOps"));
        assertTrue(e.metadata().containsKey("interactionCount"));
    }

    @Test
    void metrics_countersDoNotAccumulateAfterStop() {
        platform.stop();

        eventBus.publish(new Event(EventType.SKILL_HOT_LOAD_OPERATION,
                System.currentTimeMillis(), "test", Map.of("operation", "load", "skillName", "foo")));

        assertEquals(0L, platform.metrics().compilationOps());
    }

    @Test
    void metrics_snapshotAtIsCurrentTime() {
        long before = System.currentTimeMillis();
        PlatformMetrics metrics = platform.metrics();
        long after = System.currentTimeMillis();

        assertTrue(metrics.snapshotAt() >= before);
        assertTrue(metrics.snapshotAt() <= after);
    }
}

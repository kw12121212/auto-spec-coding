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

import static org.junit.jupiter.api.Assertions.*;

class PlatformHealthTest {

    private LealonePlatform platform;

    @BeforeEach
    void setUp() {
        platform = LealonePlatform.builder().buildPlatform();
        platform.start();
    }

    @AfterEach
    void tearDown() {
        platform.close();
    }

    // --- checkHealth() ---

    @Test
    void checkHealth_returnsFiveSubsystemsIncludingSandlock() {
        PlatformHealth health = platform.checkHealth();

        assertNotNull(health);
        assertEquals(5, health.subsystems().size());
        List<String> names = health.subsystems().stream()
                .map(SubsystemHealth::name).toList();
        assertTrue(names.contains("db"));
        assertTrue(names.contains("llm"));
        assertTrue(names.contains("compiler"));
        assertTrue(names.contains("agent"));
        assertTrue(names.contains("sandlock"));
    }

    @Test
    void checkHealth_withValidPlatform_overallStatusIsNotNull() {
        PlatformHealth health = platform.checkHealth();

        assertNotNull(health.overallStatus());
        assertTrue(health.probedAt() > 0);
    }

    @Test
    void checkHealth_withEmptyLlmRegistry_llmSubsystemIsDegraded() {
        // Platform built without any LLM providers — registry is empty
        PlatformHealth health = platform.checkHealth();

        SubsystemHealth llm = health.subsystems().stream()
                .filter(s -> "llm".equals(s.name()))
                .findFirst().orElseThrow();
        // No provider registered by default → DEGRADED
        assertEquals(SubsystemStatus.DEGRADED, llm.status());
        assertNotNull(llm.message());
    }

    @Test
    void checkHealth_agentSubsystemIsAlwaysUp() {
        PlatformHealth health = platform.checkHealth();

        SubsystemHealth agent = health.subsystems().stream()
                .filter(s -> "agent".equals(s.name()))
                .findFirst().orElseThrow();
        assertEquals(SubsystemStatus.UP, agent.status());
    }

    @Test
    void checkHealth_sandlockSubsystemIsDegradedWhenNoEffectiveProfileExists() {
        PlatformHealth health = platform.checkHealth();

        SubsystemHealth sandlock = health.subsystems().stream()
                .filter(s -> "sandlock".equals(s.name()))
                .findFirst().orElseThrow();
        assertEquals(SubsystemStatus.DEGRADED, sandlock.status());
        assertNotNull(sandlock.message());
        assertTrue(sandlock.message().contains("No effective environment profile"));
    }

    @Test
    void checkHealth_sandlockSubsystemIsUpWhenProfileAndRuntimeAreReady() {
        SimpleEventBus bus = new SimpleEventBus();
        LealonePlatform readyPlatform = new LealonePlatform(
                new LealonePlatform.DatabaseCapability("jdbc:lealone:embed:health_test_ready_db"),
                makeMinimalLlmCapability(),
                makeMinimalCompilerCapability(),
                makeMinimalInteractiveCapability(),
                new LealonePlatform.SandlockCapability(
                        new ReadySandlockRuntime(),
                        Map.of("dev", new LealonePlatform.SandlockProfile(
                                "dev",
                                "/work/dev-home",
                                java.util.List.of("/opt/jdk-25/bin"),
                                Map.of(),
                                Map.of(
                                        "maven", "/work/dev-cache/maven",
                                        "npm", "/work/dev-cache/npm",
                                        "go", "/work/dev-cache/go",
                                        "pip", "/work/dev-cache/pip"),
                                Map.of())),
                        "dev",
                        bus),
                bus);
        readyPlatform.start();

        try {
            PlatformHealth health = readyPlatform.checkHealth();
            SubsystemHealth sandlock = health.subsystems().stream()
                    .filter(s -> "sandlock".equals(s.name()))
                    .findFirst().orElseThrow();
            assertEquals(SubsystemStatus.UP, sandlock.status());
            assertNull(sandlock.message());
        } finally {
            readyPlatform.close();
        }
    }

    @Test
    void checkHealth_dbProbeFailure_marksDbAsDown() {
        // Use an unregistered JDBC scheme to guarantee a connection failure
        SimpleEventBus bus = new SimpleEventBus();
        Path cachePath = Path.of(System.getProperty("java.io.tmpdir"), "phtest-down-cache");
        LealonePlatform badPlatform = new LealonePlatform(
                new LealonePlatform.DatabaseCapability("jdbc:no_such_driver://localhost/db"),
                makeMinimalLlmCapability(),
                new LealonePlatform.CompilerCapability(
                        new org.specdriven.skill.compiler.LealoneSkillSourceCompiler(),
                        new org.specdriven.skill.compiler.LealoneClassCacheManager(cachePath),
                        new org.specdriven.skill.hotload.LealoneSkillHotLoader(
                                new org.specdriven.skill.compiler.LealoneSkillSourceCompiler(),
                                new org.specdriven.skill.compiler.LealoneClassCacheManager(cachePath),
                                false),
                        cachePath),
                makeMinimalInteractiveCapability(),
                bus);
        badPlatform.start();

        try {
            PlatformHealth health = badPlatform.checkHealth();
            SubsystemHealth db = health.subsystems().stream()
                    .filter(s -> "db".equals(s.name()))
                    .findFirst().orElseThrow();
            assertEquals(SubsystemStatus.DOWN, db.status());
            assertNotNull(db.message());
        } finally {
            badPlatform.close();
        }
    }

    @Test
    void checkHealth_publishesPlatformHealthCheckedEvent() {
        SimpleEventBus eventBus = new SimpleEventBus();
        List<Event> captured = new ArrayList<>();
        eventBus.subscribe(EventType.PLATFORM_HEALTH_CHECKED, captured::add);

        LealonePlatform p = new LealonePlatform(
                new LealonePlatform.DatabaseCapability("jdbc:lealone:embed:health_test_db"),
                makeMinimalLlmCapability(),
                makeMinimalCompilerCapability(),
                makeMinimalInteractiveCapability(),
                eventBus);
        p.start();

        try {
            p.checkHealth();
            assertEquals(1, captured.size());
            Event e = captured.get(0);
            assertEquals(EventType.PLATFORM_HEALTH_CHECKED, e.type());
            assertTrue(e.metadata().containsKey("overallStatus"));
            assertTrue(e.metadata().containsKey("probeDurationMs"));
        } finally {
            p.close();
        }
    }

    // --- PlatformHealth overall status logic ---

    @Test
    void deriveOverall_allUp_returnsUp() {
        List<SubsystemHealth> subsystems = List.of(
                SubsystemHealth.up("a"),
                SubsystemHealth.up("b"));
        assertEquals(SubsystemStatus.UP, PlatformHealth.deriveOverall(subsystems));
    }

    @Test
    void deriveOverall_oneDown_returnsDown() {
        List<SubsystemHealth> subsystems = List.of(
                SubsystemHealth.up("a"),
                SubsystemHealth.down("b", "broke"),
                SubsystemHealth.degraded("c", "slow"));
        assertEquals(SubsystemStatus.DOWN, PlatformHealth.deriveOverall(subsystems));
    }

    @Test
    void deriveOverall_oneDegradedNoDown_returnsDegraded() {
        List<SubsystemHealth> subsystems = List.of(
                SubsystemHealth.up("a"),
                SubsystemHealth.degraded("b", "no providers"));
        assertEquals(SubsystemStatus.DEGRADED, PlatformHealth.deriveOverall(subsystems));
    }

    @Test
    void platformHealthOf_derivesOverallAutomatically() {
        List<SubsystemHealth> subsystems = List.of(
                SubsystemHealth.up("a"),
                SubsystemHealth.down("b", "down"));
        PlatformHealth health = PlatformHealth.of(subsystems, System.currentTimeMillis());
        assertEquals(SubsystemStatus.DOWN, health.overallStatus());
    }

    // --- Helpers ---

    private LealonePlatform.LlmCapability makeMinimalLlmCapability() {
        return new LealonePlatform.LlmCapability(
                new org.specdriven.agent.agent.DefaultLlmProviderRegistry(null, eventBus(), null),
                java.util.Optional.empty());
    }

    private LealonePlatform.CompilerCapability makeMinimalCompilerCapability() {
        java.nio.file.Path path = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "phtest-cache");
        return new LealonePlatform.CompilerCapability(
                new org.specdriven.skill.compiler.LealoneSkillSourceCompiler(),
                new org.specdriven.skill.compiler.LealoneClassCacheManager(path),
                new org.specdriven.skill.hotload.LealoneSkillHotLoader(
                        new org.specdriven.skill.compiler.LealoneSkillSourceCompiler(),
                        new org.specdriven.skill.compiler.LealoneClassCacheManager(path),
                        false),
                path);
    }

    private LealonePlatform.InteractiveCapability makeMinimalInteractiveCapability() {
        return new LealonePlatform.InteractiveCapability(
                sessionId -> new org.specdriven.agent.interactive.LealoneAgentAdapter(
                        "jdbc:lealone:embed:health_test_db"));
    }

    private SimpleEventBus eventBus() {
        return new SimpleEventBus();
    }

    private static final class ReadySandlockRuntime implements LealonePlatform.SandlockRuntime {
        @Override
        public LealonePlatform.SandlockLaunchCheck check() {
            return LealonePlatform.SandlockLaunchCheck.ready();
        }

        @Override
        public LealonePlatform.SandlockProcessOutput execute(LealonePlatform.SandlockProfile resolvedProfile,
                                                             java.util.List<String> command) {
            return new LealonePlatform.SandlockProcessOutput(0, "", "");
        }
    }
}

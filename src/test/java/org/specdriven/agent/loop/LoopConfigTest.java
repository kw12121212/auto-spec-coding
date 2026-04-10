package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.event.SimpleEventBus;

class LoopConfigTest {

    @Test
    void defaultsFactory() {
        var bus = new SimpleEventBus();
        LoopConfig cfg = LoopConfig.defaults(Path.of("/tmp"), bus);
        assertEquals(10, cfg.maxIterations());
        assertEquals(600, cfg.iterationTimeoutSeconds());
        assertTrue(cfg.targetMilestones().isEmpty());
    }

    @Test
    void customConfig() {
        var bus = new SimpleEventBus();
        LoopConfig cfg = new LoopConfig(5, 300, List.of("m24.md"), Path.of("/project"), bus);
        assertEquals(5, cfg.maxIterations());
        assertEquals(300, cfg.iterationTimeoutSeconds());
        assertEquals(List.of("m24.md"), cfg.targetMilestones());
    }

    @Test
    void rejectsNullProjectRoot() {
        assertThrows(NullPointerException.class,
                () -> new LoopConfig(10, 600, List.of(), null, new SimpleEventBus()));
    }

    @Test
    void rejectsNullEventBus() {
        assertThrows(NullPointerException.class,
                () -> new LoopConfig(10, 600, List.of(), Path.of("/tmp"), null));
    }

    @Test
    void rejectsZeroMaxIterations() {
        assertThrows(IllegalArgumentException.class,
                () -> new LoopConfig(0, 600, List.of(), Path.of("/tmp"), new SimpleEventBus()));
    }

    @Test
    void rejectsNegativeMaxIterations() {
        assertThrows(IllegalArgumentException.class,
                () -> new LoopConfig(-1, 600, List.of(), Path.of("/tmp"), new SimpleEventBus()));
    }

    @Test
    void nullTargetMilestonesNormalizedToEmpty() {
        LoopConfig cfg = new LoopConfig(10, 600, null, Path.of("/tmp"), new SimpleEventBus());
        assertTrue(cfg.targetMilestones().isEmpty());
    }

    @Test
    void targetMilestonesIsImmutable() {
        LoopConfig cfg = new LoopConfig(10, 600, List.of("a.md"), Path.of("/tmp"), new SimpleEventBus());
        assertThrows(UnsupportedOperationException.class,
                () -> cfg.targetMilestones().add("b.md"));
    }
}

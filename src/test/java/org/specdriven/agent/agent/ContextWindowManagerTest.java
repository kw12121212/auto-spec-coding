package org.specdriven.agent.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ContextWindowManagerTest {

    @Test
    void createWithModelLimit() {
        ContextWindowManager mgr = new ContextWindowManager("gpt-4", 128000);
        assertEquals("gpt-4", mgr.modelName());
        assertEquals(128000, mgr.maxTokens());
        assertEquals(128000, mgr.remainingCapacity());
    }

    @Test
    void addUsage() {
        ContextWindowManager mgr = new ContextWindowManager("gpt-4", 128000);
        mgr.addUsage(new LlmUsage(1000, 500, 1500));
        assertEquals(126500, mgr.remainingCapacity());
    }

    @Test
    void addNullUsage() {
        ContextWindowManager mgr = new ContextWindowManager("gpt-4", 128000);
        mgr.addUsage(null);
        assertEquals(128000, mgr.remainingCapacity());
    }

    @Test
    void remainingCapacity() {
        ContextWindowManager mgr = new ContextWindowManager("gpt-4", 128000);
        mgr.addUsage(new LlmUsage(50000, 50000, 100000));
        assertEquals(28000, mgr.remainingCapacity());
    }

    @Test
    void canFitTrue() {
        ContextWindowManager mgr = new ContextWindowManager("gpt-4", 128000);
        mgr.addUsage(new LlmUsage(60000, 60000, 120000));
        assertTrue(mgr.canFit(8000));
    }

    @Test
    void canFitFalse() {
        ContextWindowManager mgr = new ContextWindowManager("gpt-4", 128000);
        mgr.addUsage(new LlmUsage(62000, 63000, 125000));
        assertFalse(mgr.canFit(4000));
    }

    @Test
    void canFitExactBoundary() {
        ContextWindowManager mgr = new ContextWindowManager("gpt-4", 128000);
        mgr.addUsage(new LlmUsage(60000, 60000, 120000));
        assertTrue(mgr.canFit(8000)); // 120000 + 8000 = 128000 == max
    }

    @Test
    void resetClearsUsage() {
        ContextWindowManager mgr = new ContextWindowManager("gpt-4", 128000);
        mgr.addUsage(new LlmUsage(100000, 10000, 110000));
        mgr.reset();
        assertEquals(128000, mgr.remainingCapacity());
    }

    @Test
    void rejectZeroMaxTokens() {
        assertThrows(IllegalArgumentException.class, () -> new ContextWindowManager("test", 0));
    }

    @Test
    void rejectNegativeMaxTokens() {
        assertThrows(IllegalArgumentException.class, () -> new ContextWindowManager("test", -1));
    }
}

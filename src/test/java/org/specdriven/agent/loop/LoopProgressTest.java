package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;

import org.junit.jupiter.api.Test;

class LoopProgressTest {

    @Test
    void nullCompletedChangeNamesNormalizedToEmpty() {
        LoopProgress p = new LoopProgress(LoopState.IDLE, null, 0);
        assertTrue(p.completedChangeNames().isEmpty());
    }

    @Test
    void defensiveCopy() {
        Set<String> names = new java.util.HashSet<>(Set.of("a", "b"));
        LoopProgress p = new LoopProgress(LoopState.RUNNING, names, 3);
        names.add("c");
        assertEquals(2, p.completedChangeNames().size());
    }

    @Test
    void immutableSet() {
        LoopProgress p = new LoopProgress(LoopState.IDLE, Set.of("a"), 0);
        assertThrows(UnsupportedOperationException.class,
                () -> p.completedChangeNames().add("x"));
    }

    @Test
    void negativeTotalIterationsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new LoopProgress(LoopState.IDLE, Set.of(), -1));
    }

    @Test
    void zeroTotalIterationsAllowed() {
        assertDoesNotThrow(() -> new LoopProgress(LoopState.IDLE, Set.of(), 0));
    }

    @Test
    void recordEquality() {
        LoopProgress a = new LoopProgress(LoopState.RUNNING, Set.of("x"), 1);
        LoopProgress b = new LoopProgress(LoopState.RUNNING, Set.of("x"), 1);
        assertEquals(a, b);
    }
}

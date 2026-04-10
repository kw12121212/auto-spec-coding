package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LoopIterationTest {

    @Test
    void validIteration() {
        LoopIteration iter = new LoopIteration(1, "my-change", "m24.md", 1000L, 2000L, IterationStatus.SUCCESS, null);
        assertEquals(1, iter.iterationNumber());
        assertEquals("my-change", iter.changeName());
        assertEquals(IterationStatus.SUCCESS, iter.status());
        assertNull(iter.failureReason());
    }

    @Test
    void failedIteration() {
        LoopIteration iter = new LoopIteration(2, "bad-change", "m24.md", 1000L, 2000L, IterationStatus.FAILED, "timeout");
        assertEquals(IterationStatus.FAILED, iter.status());
        assertEquals("timeout", iter.failureReason());
    }

    @Test
    void inProgressIterationHasNullCompletedAt() {
        LoopIteration iter = new LoopIteration(1, "change", "m24.md", 1000L, null, IterationStatus.SUCCESS, null);
        assertNull(iter.completedAt());
    }

    @Test
    void rejectsZeroIterationNumber() {
        assertThrows(IllegalArgumentException.class,
                () -> new LoopIteration(0, "change", "m24.md", 1000L, 2000L, IterationStatus.SUCCESS, null));
    }

    @Test
    void rejectsNegativeIterationNumber() {
        assertThrows(IllegalArgumentException.class,
                () -> new LoopIteration(-1, "change", "m24.md", 1000L, 2000L, IterationStatus.SUCCESS, null));
    }
}

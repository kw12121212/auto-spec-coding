package org.specdriven.agent.loop;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class LoopStateTest {

    @Test
    void idleToRecommending() {
        assertDoesNotThrow(() -> LoopState.IDLE.requireTransitionTo(LoopState.RECOMMENDING));
    }

    @Test
    void recommendingToRunning() {
        assertDoesNotThrow(() -> LoopState.RECOMMENDING.requireTransitionTo(LoopState.RUNNING));
    }

    @Test
    void recommendingToPaused() {
        assertDoesNotThrow(() -> LoopState.RECOMMENDING.requireTransitionTo(LoopState.PAUSED));
    }

    @Test
    void recommendingToStopped() {
        assertDoesNotThrow(() -> LoopState.RECOMMENDING.requireTransitionTo(LoopState.STOPPED));
    }

    @Test
    void recommendingToError() {
        assertDoesNotThrow(() -> LoopState.RECOMMENDING.requireTransitionTo(LoopState.ERROR));
    }

    @Test
    void runningToCheckpoint() {
        assertDoesNotThrow(() -> LoopState.RUNNING.requireTransitionTo(LoopState.CHECKPOINT));
    }

    @Test
    void runningToPaused() {
        assertDoesNotThrow(() -> LoopState.RUNNING.requireTransitionTo(LoopState.PAUSED));
    }

    @Test
    void runningToStopped() {
        assertDoesNotThrow(() -> LoopState.RUNNING.requireTransitionTo(LoopState.STOPPED));
    }

    @Test
    void runningToError() {
        assertDoesNotThrow(() -> LoopState.RUNNING.requireTransitionTo(LoopState.ERROR));
    }

    @Test
    void checkpointToRecommending() {
        assertDoesNotThrow(() -> LoopState.CHECKPOINT.requireTransitionTo(LoopState.RECOMMENDING));
    }

    @Test
    void checkpointToPaused() {
        assertDoesNotThrow(() -> LoopState.CHECKPOINT.requireTransitionTo(LoopState.PAUSED));
    }

    @Test
    void checkpointToStopped() {
        assertDoesNotThrow(() -> LoopState.CHECKPOINT.requireTransitionTo(LoopState.STOPPED));
    }

    @Test
    void checkpointToError() {
        assertDoesNotThrow(() -> LoopState.CHECKPOINT.requireTransitionTo(LoopState.ERROR));
    }

    @Test
    void pausedToRecommending() {
        assertDoesNotThrow(() -> LoopState.PAUSED.requireTransitionTo(LoopState.RECOMMENDING));
    }

    @Test
    void errorToIdle() {
        assertDoesNotThrow(() -> LoopState.ERROR.requireTransitionTo(LoopState.IDLE));
    }

    @Test
    void stoppedIsTerminal() {
        assertTrue(LoopState.STOPPED.isTerminal());
    }

    @Test
    void idleIsNotTerminal() {
        assertFalse(LoopState.IDLE.isTerminal());
    }

    @Test
    void stoppedRejectsAllTransitions() {
        for (LoopState target : LoopState.values()) {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                    () -> LoopState.STOPPED.requireTransitionTo(target));
            assertTrue(ex.getMessage().contains("STOPPED"));
        }
    }

    @Test
    void idleRejectsInvalidTransition() {
        assertThrows(IllegalStateException.class,
                () -> LoopState.IDLE.requireTransitionTo(LoopState.RUNNING));
    }

    @Test
    void pausedRejectsInvalidTransition() {
        assertThrows(IllegalStateException.class,
                () -> LoopState.PAUSED.requireTransitionTo(LoopState.RUNNING));
    }
}

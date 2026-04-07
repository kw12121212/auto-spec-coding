package org.specdriven.agent.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CronStatusTest {

    // -------------------------------------------------------------------------
    // Valid transitions
    // -------------------------------------------------------------------------

    @Test
    void active_toCancelled() {
        assertDoesNotThrow(() -> CronStatus.validateTransition(CronStatus.ACTIVE, CronStatus.CANCELLED));
    }

    @Test
    void active_toFired() {
        assertDoesNotThrow(() -> CronStatus.validateTransition(CronStatus.ACTIVE, CronStatus.FIRED));
    }

    // -------------------------------------------------------------------------
    // Invalid transitions
    // -------------------------------------------------------------------------

    @Test
    void sameState_isInvalid() {
        assertThrows(IllegalStateException.class,
                () -> CronStatus.validateTransition(CronStatus.ACTIVE, CronStatus.ACTIVE));
        assertThrows(IllegalStateException.class,
                () -> CronStatus.validateTransition(CronStatus.CANCELLED, CronStatus.CANCELLED));
        assertThrows(IllegalStateException.class,
                () -> CronStatus.validateTransition(CronStatus.FIRED, CronStatus.FIRED));
    }

    // -------------------------------------------------------------------------
    // Terminal states
    // -------------------------------------------------------------------------

    @Test
    void cancelled_isTerminal() {
        assertThrows(IllegalStateException.class,
                () -> CronStatus.validateTransition(CronStatus.CANCELLED, CronStatus.ACTIVE));
        assertThrows(IllegalStateException.class,
                () -> CronStatus.validateTransition(CronStatus.CANCELLED, CronStatus.FIRED));
    }

    @Test
    void fired_isTerminal() {
        assertThrows(IllegalStateException.class,
                () -> CronStatus.validateTransition(CronStatus.FIRED, CronStatus.ACTIVE));
        assertThrows(IllegalStateException.class,
                () -> CronStatus.validateTransition(CronStatus.FIRED, CronStatus.CANCELLED));
    }
}

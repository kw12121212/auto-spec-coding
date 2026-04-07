package org.specdriven.agent.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TeamStatusTest {

    // -------------------------------------------------------------------------
    // Valid transitions
    // -------------------------------------------------------------------------

    @Test
    void active_toDissolved() {
        assertDoesNotThrow(() -> TeamStatus.validateTransition(TeamStatus.ACTIVE, TeamStatus.DISSOLVED));
    }

    // -------------------------------------------------------------------------
    // Invalid transitions
    // -------------------------------------------------------------------------

    @Test
    void sameState_isInvalid() {
        assertThrows(IllegalStateException.class,
                () -> TeamStatus.validateTransition(TeamStatus.ACTIVE, TeamStatus.ACTIVE));
        assertThrows(IllegalStateException.class,
                () -> TeamStatus.validateTransition(TeamStatus.DISSOLVED, TeamStatus.DISSOLVED));
    }

    // -------------------------------------------------------------------------
    // Terminal states
    // -------------------------------------------------------------------------

    @Test
    void dissolved_isTerminal() {
        assertThrows(IllegalStateException.class,
                () -> TeamStatus.validateTransition(TeamStatus.DISSOLVED, TeamStatus.ACTIVE));
    }
}

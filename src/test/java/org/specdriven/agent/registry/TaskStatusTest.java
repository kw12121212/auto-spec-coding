package org.specdriven.agent.registry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TaskStatusTest {

    // -------------------------------------------------------------------------
    // Valid transitions
    // -------------------------------------------------------------------------

    @Test
    void pending_toInProgress() {
        assertDoesNotThrow(() -> TaskStatus.validateTransition(TaskStatus.PENDING, TaskStatus.IN_PROGRESS));
    }

    @Test
    void pending_toDeleted() {
        assertDoesNotThrow(() -> TaskStatus.validateTransition(TaskStatus.PENDING, TaskStatus.DELETED));
    }

    @Test
    void inProgress_toCompleted() {
        assertDoesNotThrow(() -> TaskStatus.validateTransition(TaskStatus.IN_PROGRESS, TaskStatus.COMPLETED));
    }

    @Test
    void inProgress_toDeleted() {
        assertDoesNotThrow(() -> TaskStatus.validateTransition(TaskStatus.IN_PROGRESS, TaskStatus.DELETED));
    }

    // -------------------------------------------------------------------------
    // Invalid transitions
    // -------------------------------------------------------------------------

    @Test
    void sameState_isInvalid() {
        assertThrows(IllegalStateException.class,
                () -> TaskStatus.validateTransition(TaskStatus.PENDING, TaskStatus.PENDING));
        assertThrows(IllegalStateException.class,
                () -> TaskStatus.validateTransition(TaskStatus.IN_PROGRESS, TaskStatus.IN_PROGRESS));
    }

    @Test
    void pending_toCompleted_isInvalid() {
        assertThrows(IllegalStateException.class,
                () -> TaskStatus.validateTransition(TaskStatus.PENDING, TaskStatus.COMPLETED));
    }

    @Test
    void inProgress_toPending_isInvalid() {
        assertThrows(IllegalStateException.class,
                () -> TaskStatus.validateTransition(TaskStatus.IN_PROGRESS, TaskStatus.PENDING));
    }

    // -------------------------------------------------------------------------
    // Terminal states
    // -------------------------------------------------------------------------

    @Test
    void completed_isTerminal() {
        assertThrows(IllegalStateException.class,
                () -> TaskStatus.validateTransition(TaskStatus.COMPLETED, TaskStatus.PENDING));
        assertThrows(IllegalStateException.class,
                () -> TaskStatus.validateTransition(TaskStatus.COMPLETED, TaskStatus.IN_PROGRESS));
        assertThrows(IllegalStateException.class,
                () -> TaskStatus.validateTransition(TaskStatus.COMPLETED, TaskStatus.DELETED));
    }

    @Test
    void deleted_isTerminal() {
        assertThrows(IllegalStateException.class,
                () -> TaskStatus.validateTransition(TaskStatus.DELETED, TaskStatus.PENDING));
        assertThrows(IllegalStateException.class,
                () -> TaskStatus.validateTransition(TaskStatus.DELETED, TaskStatus.IN_PROGRESS));
        assertThrows(IllegalStateException.class,
                () -> TaskStatus.validateTransition(TaskStatus.DELETED, TaskStatus.COMPLETED));
    }
}

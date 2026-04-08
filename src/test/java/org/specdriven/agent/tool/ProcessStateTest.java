package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ProcessStateTest {

    @Test
    void allStatesExist() {
        ProcessState[] states = ProcessState.values();
        assertEquals(5, states.length);
        assertArrayEquals(
                new ProcessState[]{ProcessState.STARTING, ProcessState.RUNNING,
                        ProcessState.COMPLETED, ProcessState.FAILED, ProcessState.STOPPED},
                states);
    }

    @Test
    void valueOfEachState() {
        assertEquals(ProcessState.STARTING, ProcessState.valueOf("STARTING"));
        assertEquals(ProcessState.RUNNING, ProcessState.valueOf("RUNNING"));
        assertEquals(ProcessState.COMPLETED, ProcessState.valueOf("COMPLETED"));
        assertEquals(ProcessState.FAILED, ProcessState.valueOf("FAILED"));
        assertEquals(ProcessState.STOPPED, ProcessState.valueOf("STOPPED"));
    }
}

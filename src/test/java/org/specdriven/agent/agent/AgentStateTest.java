package org.specdriven.agent.agent;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AgentStateTest {

    @Test
    void allStatesExist() {
        AgentState[] states = AgentState.values();
        assertEquals(5, states.length);
        assertTrue(containsState(states, AgentState.IDLE));
        assertTrue(containsState(states, AgentState.RUNNING));
        assertTrue(containsState(states, AgentState.PAUSED));
        assertTrue(containsState(states, AgentState.STOPPED));
        assertTrue(containsState(states, AgentState.ERROR));
    }

    @Test
    void valueOfLookup() {
        assertEquals(AgentState.IDLE, AgentState.valueOf("IDLE"));
        assertEquals(AgentState.RUNNING, AgentState.valueOf("RUNNING"));
        assertEquals(AgentState.ERROR, AgentState.valueOf("ERROR"));
    }

    private static boolean containsState(AgentState[] states, AgentState target) {
        for (AgentState s : states) {
            if (s == target) return true;
        }
        return false;
    }
}

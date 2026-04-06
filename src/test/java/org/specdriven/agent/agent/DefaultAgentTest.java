package org.specdriven.agent.agent;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.specdriven.agent.tool.Tool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DefaultAgentTest {

    private DefaultAgent agent;

    @BeforeEach
    void setUp() {
        agent = new DefaultAgent();
    }

    // --- Valid transitions ---

    @Test
    void idleToRunning() {
        agent.init(Map.of());
        agent.start();
        assertEquals(AgentState.RUNNING, agent.getState());
    }

    @Test
    void runningToStopped() {
        agent.init(Map.of());
        agent.start();
        agent.stop();
        assertEquals(AgentState.STOPPED, agent.getState());
    }

    @Test
    void runningToPaused() {
        agent.init(Map.of());
        agent.start();
        agent.transitionToForTest(AgentState.PAUSED);
        assertEquals(AgentState.PAUSED, agent.getState());
    }

    @Test
    void pausedToRunning() {
        agent.init(Map.of());
        agent.start();
        agent.transitionToForTest(AgentState.PAUSED);
        agent.transitionToForTest(AgentState.RUNNING);
        assertEquals(AgentState.RUNNING, agent.getState());
    }

    @Test
    void pausedToStopped() {
        agent.init(Map.of());
        agent.start();
        agent.transitionToForTest(AgentState.PAUSED);
        agent.stop();
        assertEquals(AgentState.STOPPED, agent.getState());
    }

    @Test
    void errorToStopped() {
        agent.init(Map.of());
        agent.start();
        agent.transitionToForTest(AgentState.ERROR);
        agent.stop();
        assertEquals(AgentState.STOPPED, agent.getState());
    }

    // --- Invalid transitions ---

    @Test
    void idleToStoppedThrows() {
        agent.init(Map.of());
        assertThrows(IllegalStateException.class, () -> agent.stop());
    }

    @Test
    void idleToPausedThrows() {
        agent.init(Map.of());
        assertThrows(IllegalStateException.class,
            () -> agent.transitionToForTest(AgentState.PAUSED));
    }

    @Test
    void stoppedToRunningThrows() {
        agent.init(Map.of());
        agent.start();
        agent.stop();
        assertThrows(IllegalStateException.class, () -> agent.start());
    }

    // --- Init behavior ---

    @Test
    void initSetsConfigAndState() {
        Map<String, String> cfg = Map.of("key", "value");
        agent.init(cfg);
        assertEquals(AgentState.IDLE, agent.getState());
        assertEquals("value", agent.getConfig().get("key"));
    }

    @Test
    void initCalledTwiceThrows() {
        agent.init(Map.of());
        assertThrows(IllegalStateException.class, () -> agent.init(Map.of()));
    }

    @Test
    void initWithNullConfigUsesEmptyMap() {
        agent.init(null);
        assertEquals(AgentState.IDLE, agent.getState());
        assertTrue(agent.getConfig().isEmpty());
    }

    // --- Start behavior ---

    @Test
    void startFromIdleToRunning() {
        agent.init(Map.of());
        agent.start();
        assertEquals(AgentState.RUNNING, agent.getState());
    }

    @Test
    void startFromRunningThrows() {
        agent.init(Map.of());
        agent.start();
        assertThrows(IllegalStateException.class, () -> agent.start());
    }

    // --- Execute behavior ---

    @Test
    void executeOnlyInRunningState() {
        agent.init(Map.of());
        assertThrows(IllegalStateException.class,
            () -> agent.execute(stubContext()));
        agent.start();
        assertDoesNotThrow(() -> agent.execute(stubContext()));
    }

    @Test
    void executeExceptionTransitionsToError() {
        DefaultAgent failing = new DefaultAgent() {
            @Override
            protected void doExecute(AgentContext ctx) {
                throw new RuntimeException("boom");
            }
        };
        failing.init(Map.of());
        failing.start();
        assertThrows(RuntimeException.class, () -> failing.execute(stubContext()));
        assertEquals(AgentState.ERROR, failing.getState());
    }

    // --- Close behavior ---

    @Test
    void closeFromAnyState() {
        // From uninitialized (null state)
        agent.close();
        assertEquals(AgentState.STOPPED, agent.getState());

        // From IDLE
        agent = new DefaultAgent();
        agent.init(Map.of());
        agent.close();
        assertEquals(AgentState.STOPPED, agent.getState());

        // From RUNNING
        agent = new DefaultAgent();
        agent.init(Map.of());
        agent.start();
        agent.close();
        assertEquals(AgentState.STOPPED, agent.getState());

        // From STOPPED
        agent = new DefaultAgent();
        agent.init(Map.of());
        agent.start();
        agent.stop();
        agent.close();
        assertEquals(AgentState.STOPPED, agent.getState());
    }

    @Test
    void closeClearsConfig() {
        agent.init(Map.of("key", "value"));
        agent.close();
        assertTrue(agent.getConfig().isEmpty());
    }

    // --- Stop behavior ---

    @Test
    void stopFromRunning() {
        agent.init(Map.of());
        agent.start();
        agent.stop();
        assertEquals(AgentState.STOPPED, agent.getState());
    }

    @Test
    void stopFromPaused() {
        agent.init(Map.of());
        agent.start();
        agent.transitionToForTest(AgentState.PAUSED);
        agent.stop();
        assertEquals(AgentState.STOPPED, agent.getState());
    }

    @Test
    void stopFromError() {
        agent.init(Map.of());
        agent.start();
        agent.transitionToForTest(AgentState.ERROR);
        agent.stop();
        assertEquals(AgentState.STOPPED, agent.getState());
    }

    @Test
    void stopFromIdleThrows() {
        agent.init(Map.of());
        assertThrows(IllegalStateException.class, () -> agent.stop());
    }

    @Test
    void stopFromStoppedThrows() {
        agent.init(Map.of());
        agent.start();
        agent.stop();
        assertThrows(IllegalStateException.class, () -> agent.stop());
    }

    // --- Helper ---

    private static AgentContext stubContext() {
        return new AgentContext() {
            @Override public String sessionId() { return "test-session"; }
            @Override public Map<String, String> config() { return Map.of(); }
            @Override public Map<String, Tool> toolRegistry() { return Map.of(); }
        };
    }
}

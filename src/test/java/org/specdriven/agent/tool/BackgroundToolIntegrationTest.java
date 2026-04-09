package org.specdriven.agent.tool;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.specdriven.agent.agent.AgentContext;
import org.specdriven.agent.agent.AgentState;
import org.specdriven.agent.agent.Conversation;
import org.specdriven.agent.agent.DefaultAgent;
import org.specdriven.agent.agent.SimpleAgentContext;
import org.specdriven.agent.event.EventBus;
import org.specdriven.agent.event.SimpleEventBus;

/**
 * Integration tests for background tool lifecycle management with Agent.
 */
class BackgroundToolIntegrationTest {

    @Test
    void agentContextReturnsCorrectProcessManager() {
        EventBus eventBus = new SimpleEventBus();
        DefaultProcessManager processManager = new DefaultProcessManager(eventBus);

        // Create context with process manager
        SimpleAgentContext contextWithPm = new SimpleAgentContext(
                "test-session",
                Collections.emptyMap(),
                Collections.emptyMap(),
                new Conversation(),
                null,
                processManager
        );

        // Verify processManager() returns the correct instance
        Optional<ProcessManager> pmOpt = contextWithPm.processManager();
        assertTrue(pmOpt.isPresent());
        assertSame(processManager, pmOpt.get());
    }

    @Test
    void backwardCompatibilityWithoutProcessManager() {
        // Test old constructor without process manager
        SimpleAgentContext context = new SimpleAgentContext(
                "test-session",
                Collections.emptyMap(),
                Collections.emptyMap(),
                new Conversation(),
                null  // no session store, no process manager
        );

        // Should return empty optional
        assertTrue(context.processManager().isEmpty());
    }

    @Test
    void agentWorksWithoutProcessManager() {
        // Create context without process manager
        SimpleAgentContext context = new SimpleAgentContext(
                "test-session",
                Collections.emptyMap(),
                Collections.emptyMap(),
                new Conversation()
        );

        // Verify processManager() returns empty
        assertTrue(context.processManager().isEmpty());

        // Create and lifecycle agent - should not fail
        DefaultAgent agent = new DefaultAgent();
        agent.init(Collections.emptyMap());
        agent.start();

        // Stop should work even without process manager
        assertDoesNotThrow(() -> agent.stop());
        assertEquals(AgentState.STOPPED, agent.getState());

        agent.close();
    }

    @Test
    void agentContextWithProcessManagerIntegration() {
        EventBus eventBus = new SimpleEventBus();
        DefaultProcessManager processManager = new DefaultProcessManager(eventBus);

        // Create context with process manager
        SimpleAgentContext context = new SimpleAgentContext(
                "test-session",
                Collections.emptyMap(),
                Collections.emptyMap(),
                new Conversation(),
                null,
                processManager
        );

        // Verify the context properly exposes the process manager
        assertTrue(context.processManager().isPresent());
        assertSame(processManager, context.processManager().get());

        // Create agent and verify lifecycle works
        DefaultAgent agent = new DefaultAgent();
        agent.init(Collections.emptyMap());
        assertEquals(AgentState.IDLE, agent.getState());

        agent.start();
        assertEquals(AgentState.RUNNING, agent.getState());

        // Stop should complete without errors
        agent.stop();
        assertEquals(AgentState.STOPPED, agent.getState());

        agent.close();
    }

    @Test
    void processManagerStopAllCalledOnAgentStop() {
        // Create a spy process manager that tracks stopAll calls
        EventBus eventBus = new SimpleEventBus();
        AtomicInteger stopAllCount = new AtomicInteger(0);

        ProcessManager spyProcessManager = new ProcessManager() {
            @Override
            public BackgroundProcessHandle register(Process process, String toolName, String command) {
                return null;
            }

            @Override
            public BackgroundProcessHandle registerWithProbe(Process process, String toolName, String command, ReadyProbe probe) {
                return null;
            }

            @Override
            public boolean waitForReady(String processId, java.time.Duration timeout) {
                return false;
            }

            @Override
            public boolean cleanup(String processId) {
                return false;
            }

            @Override
            public Optional<ProcessState> getState(String processId) {
                return Optional.empty();
            }

            @Override
            public Optional<ProcessOutput> getOutput(String processId) {
                return Optional.empty();
            }

            @Override
            public java.util.List<BackgroundProcessHandle> listActive() {
                return Collections.emptyList();
            }

            @Override
            public boolean stop(String processId) {
                return false;
            }

            @Override
            public int stopAll() {
                stopAllCount.incrementAndGet();
                return 0;
            }
        };

        // Create context with spy process manager
        SimpleAgentContext context = new SimpleAgentContext(
                "test-session",
                Collections.emptyMap(),
                Collections.emptyMap(),
                new Conversation(),
                null,
                spyProcessManager
        );

        // Create and run agent
        DefaultAgent agent = new DefaultAgent();
        agent.init(Collections.emptyMap());
        agent.start();

        // Execute in a separate thread since it would block
        Thread execThread = new Thread(() -> {
            try {
                agent.execute(context);
            } catch (IllegalStateException e) {
                // Expected - no LLM client configured
            }
        });
        execThread.start();
        try {
            execThread.join(100); // Wait briefly for execution to start
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Initially stopAll should not have been called
        assertEquals(0, stopAllCount.get());

        // Stop the agent - this should trigger cleanup
        agent.stop();

        // Verify stopAll was called during cleanup
        assertEquals(1, stopAllCount.get());

        agent.close();
    }

    @Test
    void agentInterfaceDefaultProcessManagerReturnsEmpty() {
        // Create a minimal AgentContext implementation
        AgentContext context = new AgentContext() {
            @Override
            public String sessionId() {
                return "test";
            }

            @Override
            public Map<String, String> config() {
                return Collections.emptyMap();
            }

            @Override
            public Map<String, org.specdriven.agent.tool.Tool> toolRegistry() {
                return Collections.emptyMap();
            }
        };

        // Default implementation should return empty
        assertTrue(context.processManager().isEmpty());
    }

    @Test
    void backwardCompatibilityOldConstructor() {
        // Test the 4-arg constructor (no session store, no process manager)
        SimpleAgentContext context4 = new SimpleAgentContext(
                "test-session",
                Collections.emptyMap(),
                Collections.emptyMap(),
                new Conversation()
        );
        assertTrue(context4.processManager().isEmpty());

        // Test the 5-arg constructor (with session store, no process manager)
        SimpleAgentContext context5 = new SimpleAgentContext(
                "test-session",
                Collections.emptyMap(),
                Collections.emptyMap(),
                new Conversation(),
                null
        );
        assertTrue(context5.processManager().isEmpty());
    }
}

package org.specdriven.agent.agent;

import java.util.Collections;
import java.util.Map;

/**
 * Default implementation of the Agent interface with a state machine
 * enforcing valid lifecycle transitions: init → start → execute → stop → close.
 */
public class DefaultAgent implements Agent {

    private volatile AgentState state = null;
    private volatile Map<String, String> config = Collections.emptyMap();

    @Override
    public void init(Map<String, String> config) {
        if (this.state != null) {
            throw new IllegalStateException(
                "Agent already initialized (current state: " + this.state + ")");
        }
        this.config = config != null ? config : Collections.emptyMap();
        this.state = AgentState.IDLE;
    }

    @Override
    public void start() {
        transitionTo(AgentState.RUNNING);
    }

    @Override
    public void execute(AgentContext context) {
        if (state != AgentState.RUNNING) {
            throw new IllegalStateException(
                "execute() requires RUNNING state, current: " + state);
        }
        try {
            doExecute(context);
        } catch (Exception e) {
            state = AgentState.ERROR;
            throw e;
        }
    }

    @Override
    public void stop() {
        if (state != AgentState.RUNNING
            && state != AgentState.PAUSED
            && state != AgentState.ERROR) {
            throw new IllegalStateException(
                "stop() requires RUNNING, PAUSED, or ERROR state, current: " + state);
        }
        state = AgentState.STOPPED;
    }

    @Override
    public void close() {
        state = AgentState.STOPPED;
        config = Collections.emptyMap();
    }

    @Override
    public AgentState getState() {
        return state;
    }

    /**
     * Returns the configuration provided via init().
     */
    public Map<String, String> getConfig() {
        return config;
    }

    /**
     * Hook for subclasses to override execute behavior.
     * Default implementation delegates to the orchestrator loop.
     */
    protected void doExecute(AgentContext context) {
        OrchestratorConfig orchestratorConfig = OrchestratorConfig.fromMap(config);
        Orchestrator orchestrator = new DefaultOrchestrator(orchestratorConfig, this::getState);

        LlmClient llmClient = createLlmClient(context);
        orchestrator.run(context, llmClient);
    }

    /**
     * Creates the LLM client for this execution.
     * Default returns null — subclasses or config should override.
     */
    protected LlmClient createLlmClient(AgentContext context) {
        return null;
    }

    /**
     * Forces a state transition for testing PAUSED transitions
     * (pause/resume API is reserved for orchestrator).
     */
    void transitionToForTest(AgentState target) {
        transitionTo(target);
    }

    private void transitionTo(AgentState target) {
        AgentState current = this.state;
        if (!isValidTransition(current, target)) {
            throw new IllegalStateException(
                "Invalid transition: " + current + " → " + target);
        }
        this.state = target;
    }

    private static boolean isValidTransition(AgentState from, AgentState to) {
        if (from == null || to == null) return false;
        if (from == to) return false;
        return switch (from) {
            case IDLE -> to == AgentState.RUNNING;
            case RUNNING -> to == AgentState.STOPPED
                || to == AgentState.PAUSED
                || to == AgentState.ERROR;
            case PAUSED -> to == AgentState.RUNNING
                || to == AgentState.STOPPED;
            case ERROR -> to == AgentState.STOPPED;
            case STOPPED -> false;
        };
    }
}

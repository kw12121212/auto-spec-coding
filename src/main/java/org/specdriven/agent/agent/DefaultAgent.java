package org.specdriven.agent.agent;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.specdriven.agent.hook.PermissionCheckHook;
import org.specdriven.agent.hook.ToolExecutionHook;

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
     * If the context carries a {@link SessionStore} and a non-null session ID,
     * conversation history is restored before the orchestrator runs and
     * persisted after it completes (or on error).
     */
    protected void doExecute(AgentContext context) {
        SessionStore store = context instanceof SimpleAgentContext sac ? sac.sessionStore() : null;

        long createdAt = System.currentTimeMillis();
        long expiryAt = createdAt + Session.TTL_MS;

        if (store != null && context.sessionId() != null) {
            Optional<Session> stored = store.load(context.sessionId());
            if (stored.isPresent()) {
                Session existing = stored.get();
                createdAt = existing.createdAt();
                expiryAt = existing.expiryAt();
                existing.conversation().history().forEach(context.conversation()::append);
            }
        }

        OrchestratorConfig orchestratorConfig = buildOrchestratorConfig();
        Orchestrator orchestrator = new DefaultOrchestrator(orchestratorConfig, this::getState);
        LlmClient llmClient = createLlmClient(context);

        final long sessionCreatedAt = createdAt;
        final long sessionExpiryAt = expiryAt;
        try {
            orchestrator.run(context, llmClient);
        } finally {
            if (store != null && context.sessionId() != null) {
                long now = System.currentTimeMillis();
                Session toSave = new Session(
                        context.sessionId(), state,
                        sessionCreatedAt, now, sessionExpiryAt,
                        context.conversation());
                store.save(toSave);
            }
        }
    }

    /**
     * Creates the LLM client for this execution.
     * Default returns null — subclasses or config should override.
     */
    protected LlmClient createLlmClient(AgentContext context) {
        return null;
    }

    /**
     * Builds the OrchestratorConfig with permission check hook.
     */
    protected OrchestratorConfig buildOrchestratorConfig() {
        OrchestratorConfig base = OrchestratorConfig.fromMap(config);
        List<ToolExecutionHook> hooks = List.of(new PermissionCheckHook());
        return new OrchestratorConfig(base.maxTurns(), base.toolTimeoutSeconds(), hooks);
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

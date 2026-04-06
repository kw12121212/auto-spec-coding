package org.specdriven.agent.agent;

import java.util.Map;

/**
 * Contract for an agent following a lifecycle of init → start → execute → stop → close.
 */
public interface Agent {

    /**
     * Initializes the agent with the given configuration.
     */
    void init(Map<String, String> config);

    /**
     * Starts the agent, transitioning to RUNNING state.
     */
    void start();

    /**
     * Stops the agent, transitioning to STOPPED state.
     */
    void stop();

    /**
     * Closes the agent and releases all resources.
     */
    void close();

    /**
     * Returns the current lifecycle state.
     */
    AgentState getState();

    /**
     * Executes the agent's main logic with the given context.
     *
     * @param context the agent execution context
     */
    void execute(AgentContext context);
}

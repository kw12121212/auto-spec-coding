package org.specdriven.agent.agent;

/**
 * Orchestrates the receive → think → act → observe loop
 * for a single agent execution.
 */
public interface Orchestrator {

    /**
     * Runs the full orchestration loop.
     *
     * @param context   the agent execution context
     * @param llmClient the LLM backend to call
     */
    void run(AgentContext context, LlmClient llmClient);
}

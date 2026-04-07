package org.specdriven.agent.agent;

import java.util.List;

/**
 * Provider-agnostic abstraction for LLM chat calls.
 * M5 provides concrete implementations; tests use mocks.
 */
public interface LlmClient {

    /**
     * Sends the conversation history to the LLM and returns its response.
     *
     * @param messages the full conversation history
     * @return the LLM response — either text or tool-call requests
     */
    LlmResponse chat(List<Message> messages);
}

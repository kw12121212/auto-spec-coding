package org.specdriven.agent.agent;

import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    /**
     * Sends a structured request to the LLM.
     * Default implementation delegates to {@link #chat(List)}.
     *
     * @param request the structured request
     * @return the LLM response
     */
    default LlmResponse chat(LlmRequest request) {
        return chat(request.messages());
    }

    /**
     * Starts a streaming LLM response.
     * Default implementation throws UnsupportedOperationException.
     *
     * @param request  the structured request
     * @param callback the stream callback
     */
    default void chatStreaming(LlmRequest request, LlmStreamCallback callback) {
        throw new UnsupportedOperationException("Streaming not supported by default implementation");
    }
}

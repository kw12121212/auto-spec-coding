package org.specdriven.agent.agent;

import java.util.List;
import java.util.Objects;

/**
 * Produces an optimized message list for LLM request preparation.
 */
public interface ToolResultFilter {

    /**
     * Filters candidate messages using explicit current-turn and retention inputs.
     */
    List<Message> filter(ToolResultFilterInput input);

    /**
     * Applies this filter to an existing request while preserving every non-message parameter.
     */
    default LlmRequest filterRequest(LlmRequest request, ToolResultFilterInput input) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(input, "input");
        List<Message> filteredMessages = filter(input.withMessages(request.messages()));
        return new LlmRequest(
                filteredMessages,
                request.systemPrompt(),
                request.tools(),
                request.temperature(),
                request.maxTokens(),
                request.extra());
    }
}

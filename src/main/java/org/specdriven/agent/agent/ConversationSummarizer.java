package org.specdriven.agent.agent;

import java.util.List;
import java.util.Objects;

/**
 * Produces an optimized conversation history for LLM request preparation.
 */
public interface ConversationSummarizer {

    /**
     * Summarizes candidate messages using explicit window, budget, and retention inputs.
     */
    List<Message> summarize(ConversationSummarizerInput input);

    /**
     * Applies this summarizer to an existing request while preserving every non-message parameter.
     */
    default LlmRequest summarizeRequest(LlmRequest request, ConversationSummarizerInput input) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(input, "input");
        List<Message> summarizedMessages = summarize(input.withMessages(request.messages()));
        return new LlmRequest(
                summarizedMessages,
                request.systemPrompt(),
                request.tools(),
                request.temperature(),
                request.maxTokens(),
                request.extra());
    }
}

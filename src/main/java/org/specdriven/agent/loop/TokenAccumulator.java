package org.specdriven.agent.loop;

import org.specdriven.agent.agent.LlmClient;
import org.specdriven.agent.agent.LlmResponse;
import org.specdriven.agent.agent.LlmUsage;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Wraps an {@link LlmClient} and accumulates total token usage
 * from every LLM response. Thread-safe.
 */
class TokenAccumulator implements LlmClient {

    private final LlmClient delegate;
    private final AtomicLong totalTokens = new AtomicLong();

    TokenAccumulator(LlmClient delegate) {
        this.delegate = delegate;
    }

    @Override
    public LlmResponse chat(List<org.specdriven.agent.agent.Message> messages) {
        LlmResponse response = delegate.chat(messages);
        accumulateUsage(response);
        return response;
    }

    @Override
    public LlmResponse chat(org.specdriven.agent.agent.LlmRequest request) {
        LlmResponse response = delegate.chat(request);
        accumulateUsage(response);
        return response;
    }

    long totalTokens() {
        return totalTokens.get();
    }

    private void accumulateUsage(LlmResponse response) {
        if (response instanceof LlmResponse.TextResponse text && text.usage() != null) {
            totalTokens.addAndGet(text.usage().totalTokens());
        } else if (response instanceof LlmResponse.ToolCallResponse tool && tool.usage() != null) {
            totalTokens.addAndGet(tool.usage().totalTokens());
        }
    }
}

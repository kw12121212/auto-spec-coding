package org.specdriven.agent.agent;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable token usage statistics for an LLM call.
 *
 * @param promptTokens     number of tokens in the prompt
 (non-negative)
 @param completionTokens number of tokens in the completion (non-negative)
 @param totalTokens      total tokens used ( prompt + completion) (non-negative)
 */
public record LlmUsage(
        int promptTokens,
        int completionTokens,
        int totalTokens
 ) {
    public LlmUsage {
        if (promptTokens < 0) throw new IllegalArgumentException("promptTokens must be non-negative");
        if (completionTokens < 0) throw new IllegalArgumentException("completionTokens must be non-negative");        if (totalTokens < 0) throw new IllegalArgumentException("totalTokens must be non-negative");    }
}

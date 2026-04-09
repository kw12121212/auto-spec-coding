package org.specdriven.agent.llm;

/**
 * A persisted record of token usage for a single LLM call.
 */
public record UsageRecord(
        long id,
        String sessionId,
        String agentName,
        String model,
        int promptTokens,
        int completionTokens,
        int totalTokens,
        long createdAt
) {
}

package org.specdriven.agent.loop;

/**
 * Configuration for context window budget tracking.
 *
 * @param maxTokens               maximum context window size in tokens (must be positive)
 * @param warningThresholdPercent percentage of context window at which exhaustion
 *                                is triggered (1-99, default 20)
 * @param modelName               optional model name for identification (nullable)
 */
public record ContextBudget(
        int maxTokens,
        int warningThresholdPercent,
        String modelName
) {
    public ContextBudget {
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be positive");
        }
        if (warningThresholdPercent < 1 || warningThresholdPercent > 99) {
            throw new IllegalArgumentException("warningThresholdPercent must be between 1 and 99");
        }
    }

    /**
     * Creates a ContextBudget with default threshold (20%) and no model name.
     */
    public static ContextBudget of(int maxTokens) {
        return new ContextBudget(maxTokens, 20, null);
    }

    /**
     * Creates a ContextBudget with specified threshold and no model name.
     */
    public static ContextBudget of(int maxTokens, int warningThresholdPercent) {
        return new ContextBudget(maxTokens, warningThresholdPercent, null);
    }
}
